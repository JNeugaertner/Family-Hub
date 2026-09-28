package de.familyhub.rewards;

import static de.familyhub.testsupport.TestUsers.as;
import static org.hamcrest.Matchers.contains;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import de.familyhub.family.FamilyMember;
import de.familyhub.family.FamilyMemberRepository;
import de.familyhub.permission.Role;
import de.familyhub.testsupport.TestUsers;

// Belohnungen verwalten: nur Administratoren; Kinder sehen nur aktive Belohnungen, Gäste keine.
@SpringBootTest
@AutoConfigureMockMvc
class RewardControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private RewardRepository rewards;

    @Autowired
    private RedemptionRepository redemptions;

    @Autowired
    private FamilyMemberRepository members;

    private FamilyMember sarah;
    private FamilyMember lucas;
    private FamilyMember oma;
    private Reward pizza;

    @BeforeEach
    void setUp() {
        redemptions.deleteAll();
        rewards.deleteAll();
        members.deleteAll();
        sarah = members.save(TestUsers.member("Sarah", Role.ADMINISTRATOR));
        lucas = members.save(TestUsers.member("Lucas", Role.KIND));
        oma = members.save(TestUsers.member("Oma", Role.GAST));
        pizza = rewards.save(new Reward(null, "🍕", "Pizza", null, 200, RewardCategory.ESSEN, true, true));
        rewards.save(new Reward(null, "🛍️", "Gutschein", null, 500, RewardCategory.GESCHENK, false, false));
    }

    private static String json(String name, int cost, boolean active, boolean repeatable) {
        return """
                {"emoji": "🎬", "name": "%s", "description": "Du wählst den Film.", "cost": %d,
                 "category": "freizeit", "active": %s, "repeatable": %s}
                """.formatted(name, cost, active, repeatable);
    }

    @Test
    void childSeesOnlyActiveRewardsAndGuestNone() throws Exception {
        mvc.perform(get("/api/rewards").with(as(lucas)))
                .andExpect(jsonPath("$[*].name", contains("Pizza")));
        mvc.perform(get("/api/rewards").with(as(sarah)))
                .andExpect(jsonPath("$[*].name", contains("Pizza", "Gutschein")));
        mvc.perform(get("/api/rewards").with(as(oma)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Keine Berechtigung, Belohnungen anzusehen."));
    }

    @Test
    void administratorCreatesUpdatesAndDeletesRewards() throws Exception {
        String location = mvc.perform(post("/api/rewards").with(as(sarah)).contentType(APPLICATION_JSON)
                        .content(json("Film-Abend", 150, true, false)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.category").value("freizeit"))
                .andExpect(jsonPath("$.repeatable").value(false))
                .andReturn().getResponse().getHeader("Location");
        String id = location.substring(location.lastIndexOf('/') + 1);

        mvc.perform(put("/api/rewards/" + id).with(as(sarah)).contentType(APPLICATION_JSON)
                        .content(json("Film-Abend Wahl", 120, false, true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cost").value(120))
                .andExpect(jsonPath("$.active").value(false));

        mvc.perform(delete("/api/rewards/" + id).with(as(sarah))).andExpect(status().isNoContent());
        mvc.perform(delete("/api/rewards/" + id).with(as(sarah))).andExpect(status().isNotFound());
    }

    @Test
    void invalidRewardsAreRejected() throws Exception {
        mvc.perform(post("/api/rewards").with(as(sarah)).contentType(APPLICATION_JSON)
                        .content("{\"emoji\": \"🎬\", \"name\": \" \", \"cost\": 0, \"category\": \"freizeit\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").value("Titel darf nicht leer sein"))
                .andExpect(jsonPath("$.errors.cost").value("Eine Belohnung kostet mindestens 1 Punkt"));
    }

    @Test
    void onlyAdministratorsManageRewards() throws Exception {
        mvc.perform(post("/api/rewards").with(as(lucas)).contentType(APPLICATION_JSON)
                        .content(json("Film", 10, true, true)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Nur Administratoren dürfen Belohnungen verwalten."));
        mvc.perform(delete("/api/rewards/" + pizza.id()).with(as(lucas))).andExpect(status().isForbidden());
    }

    @Test
    void rewardWithOpenRedemptionCannotBeDeleted() throws Exception {
        redemptions.save(new Redemption(null, pizza.id(), "Pizza", "🍕", 200, lucas.id(), RedemptionStatus.PENDING,
                LocalDateTime.now(), lucas.id(), null, null, null));

        mvc.perform(delete("/api/rewards/" + pizza.id()).with(as(sarah)))
                .andExpect(status().isConflict());
    }
}
