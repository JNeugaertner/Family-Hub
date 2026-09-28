package de.familyhub.rewards;

import static de.familyhub.testsupport.TestUsers.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

import de.familyhub.family.FamilyMember;
import de.familyhub.family.FamilyMemberRepository;
import de.familyhub.permission.Role;
import de.familyhub.points.PointEntryRepository;
import de.familyhub.points.PointsService;
import de.familyhub.testsupport.TestUsers;

// Belohnungen einlösen (Entscheidungen vom 28.09.2026): Punkte werden sofort abgezogen, Eltern genehmigen oder
// lehnen ab (Punkte zurück), offene Einlösungen lassen sich zurückziehen (Punkte zurück).
@SpringBootTest
@AutoConfigureMockMvc
class RedemptionTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private RewardRepository rewards;

    @Autowired
    private RedemptionRepository redemptions;

    @Autowired
    private FamilyMemberRepository members;

    @Autowired
    private PointEntryRepository pointEntries;

    @Autowired
    private PointsService points;

    private FamilyMember sarah;
    private FamilyMember emma;
    private FamilyMember lucas;
    private FamilyMember oma;
    private Reward pizza;
    private Reward park;

    @BeforeEach
    void setUp() {
        redemptions.deleteAll();
        rewards.deleteAll();
        pointEntries.deleteAll();
        members.deleteAll();
        sarah = members.save(TestUsers.member("Sarah", Role.ADMINISTRATOR));
        emma = members.save(TestUsers.member("Emma", Role.JUGENDLICHER));
        lucas = members.save(TestUsers.member("Lucas", Role.KIND));
        oma = members.save(TestUsers.member("Oma", Role.GAST));
        pizza = rewards.save(new Reward(null, "🍕", "Pizza", null, 200, RewardCategory.ESSEN, true, true));
        park = rewards.save(new Reward(null, "🎪", "Freizeitpark", null, 300, RewardCategory.AUSFLUG, true, false));
        points.book(lucas.id(), 700, "Startguthaben", sarah.id());
        points.book(emma.id(), 100, "Startguthaben", sarah.id());
    }

    private String redeem(FamilyMember by, Reward reward) throws Exception {
        String body = mvc.perform(post("/api/redemptions").with(as(by)).contentType(APPLICATION_JSON)
                        .content("{\"rewardId\": \"" + reward.id() + "\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(body, "$.id");
    }

    @Test
    void childRedeemsPointsAreDeductedImmediatelyAndParentApproves() throws Exception {
        String id = redeem(lucas, pizza);

        assertThat(points.balance(lucas.id())).isEqualTo(500);
        mvc.perform(get("/api/redemptions?status=pending").with(as(sarah)))
                .andExpect(jsonPath("$[*].rewardName", contains("Pizza")))
                .andExpect(jsonPath("$[0].memberId").value(lucas.id()));

        mvc.perform(post("/api/redemptions/" + id + "/approve").with(as(sarah)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("approved"))
                .andExpect(jsonPath("$.decidedBy").value(sarah.id()));
        assertThat(points.balance(lucas.id())).isEqualTo(500);

        mvc.perform(post("/api/redemptions/" + id + "/approve").with(as(sarah)))
                .andExpect(status().isConflict());
        mvc.perform(get("/api/points/history?memberId=" + lucas.id()).with(as(lucas)))
                .andExpect(jsonPath("$[0].amount").value(-200))
                .andExpect(jsonPath("$[0].reason").value("Belohnung eingelöst: Pizza"));
    }

    @Test
    void rejectingRefundsPointsAndShowsTheReason() throws Exception {
        String id = redeem(lucas, pizza);

        mvc.perform(post("/api/redemptions/" + id + "/reject").with(as(sarah)).contentType(APPLICATION_JSON)
                        .content("{\"reason\": \"Diese Woche nicht, am Samstag gerne.\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("rejected"));

        assertThat(points.balance(lucas.id())).isEqualTo(700);
        mvc.perform(get("/api/redemptions").with(as(lucas)))
                .andExpect(jsonPath("$[0].rejectReason").value("Diese Woche nicht, am Samstag gerne."));
    }

    @Test
    void childWithdrawsOpenRedemptionAndGetsPointsBack() throws Exception {
        String id = redeem(lucas, pizza);

        mvc.perform(delete("/api/redemptions/" + id).with(as(lucas))).andExpect(status().isNoContent());

        assertThat(points.balance(lucas.id())).isEqualTo(700);
        assertThat(redemptions.count()).isZero();
    }

    @Test
    void decidedRedemptionsCannotBeWithdrawn() throws Exception {
        String id = redeem(lucas, pizza);
        mvc.perform(post("/api/redemptions/" + id + "/approve").with(as(sarah)));

        mvc.perform(delete("/api/redemptions/" + id).with(as(lucas)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Über diese Einlösung ist bereits entschieden."));
    }

    @Test
    void notEnoughPointsIsRejected() throws Exception {
        mvc.perform(post("/api/redemptions").with(as(emma)).contentType(APPLICATION_JSON)
                        .content("{\"rewardId\": \"" + pizza.id() + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Nicht genug Punkte: 100 von 200 vorhanden."));
        assertThat(points.balance(emma.id())).isEqualTo(100);
    }

    @Test
    void repeatableRewardAllowsOnlyOneOpenRedemptionAtATime() throws Exception {
        String first = redeem(lucas, pizza);
        mvc.perform(post("/api/redemptions").with(as(lucas)).contentType(APPLICATION_JSON)
                        .content("{\"rewardId\": \"" + pizza.id() + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Für diese Belohnung wartet schon eine Einlösung auf die Eltern."));

        mvc.perform(post("/api/redemptions/" + first + "/approve").with(as(sarah)));
        redeem(lucas, pizza);
        assertThat(points.balance(lucas.id())).isEqualTo(300);
    }

    @Test
    void oneTimeRewardCanBeRedeemedOnlyOnce() throws Exception {
        String id = redeem(lucas, park);
        mvc.perform(post("/api/redemptions/" + id + "/approve").with(as(sarah)));

        mvc.perform(post("/api/redemptions").with(as(lucas)).contentType(APPLICATION_JSON)
                        .content("{\"rewardId\": \"" + park.id() + "\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Diese Belohnung kann man nur einmal einlösen."));
    }

    @Test
        void parentCannotRedeemForSelfOrChild() throws Exception {
                mvc.perform(post("/api/redemptions").with(as(sarah)).contentType(APPLICATION_JSON)
                                                .content("{\"rewardId\": \"" + pizza.id() + "\"}"))
                                .andExpect(status().isForbidden())
                                .andExpect(jsonPath("$.detail").value("Nur Kinder und Jugendliche dürfen Belohnungen einlösen."));

        mvc.perform(post("/api/redemptions").with(as(sarah)).contentType(APPLICATION_JSON)
                        .content("{\"rewardId\": \"" + pizza.id() + "\", \"memberId\": \"" + lucas.id() + "\"}"))
                                .andExpect(status().isForbidden())
                                .andExpect(jsonPath("$.detail").value("Du darfst Belohnungen nur für dich selbst einlösen."));

                assertThat(points.balance(lucas.id())).isEqualTo(700);
    }

    @Test
    void childsCannotRedeemForOthersOrDecide() throws Exception {
        mvc.perform(post("/api/redemptions").with(as(emma)).contentType(APPLICATION_JSON)
                        .content("{\"rewardId\": \"" + pizza.id() + "\", \"memberId\": \"" + lucas.id() + "\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Du darfst Belohnungen nur für dich selbst einlösen."));

        String id = redeem(lucas, pizza);
        mvc.perform(post("/api/redemptions/" + id + "/approve").with(as(lucas)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Nur Administratoren dürfen Einlösungen genehmigen oder ablehnen."));
        mvc.perform(delete("/api/redemptions/" + id).with(as(emma)))
                .andExpect(status().isForbidden());
    }

    @Test
    void childSeesOnlyOwnRedemptionsAndGuestNone() throws Exception {
        redeem(lucas, pizza);
        points.book(emma.id(), 200, "Bonus", sarah.id());
        redeem(emma, pizza);

        mvc.perform(get("/api/redemptions").with(as(lucas)))
                .andExpect(jsonPath("$[*].memberId", contains(lucas.id())));
        mvc.perform(get("/api/redemptions?memberId=" + emma.id()).with(as(lucas)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/redemptions").with(as(oma))).andExpect(status().isForbidden());
        mvc.perform(post("/api/redemptions").with(as(oma)).contentType(APPLICATION_JSON)
                        .content("{\"rewardId\": \"" + pizza.id() + "\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void memberWithOpenRedemptionCannotBeDeleted() throws Exception {
        redeem(lucas, pizza);

        mvc.perform(delete("/api/members/" + lucas.id()).with(as(sarah)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Das Familienmitglied hat noch offene Einlösungen von Belohnungen. "
                        + "Bitte zuerst genehmigen oder ablehnen."));
    }
}
