package de.familyhub.task;

import static de.familyhub.testsupport.TestUsers.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
import de.familyhub.testsupport.TestUsers;

// Bonus-Aufgaben (Entscheidungen vom 29.09.2026): Eltern legen sie ohne Person an, Kinder und Jugendliche
// übernehmen sie (wer zuerst kommt), geben sie zurück, solange sie nicht erledigt sind; nach der Bestätigung gibt es
// die Punkte, wiederkehrende sind danach wieder offen.
@SpringBootTest
@AutoConfigureMockMvc
class BonusTaskTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private TaskRepository tasks;

    @Autowired
    private FamilyMemberRepository members;

    @Autowired
    private PointEntryRepository points;

    private FamilyMember sarah;
    private FamilyMember emma;
    private FamilyMember lucas;
    private FamilyMember lily;
    private FamilyMember oma;

    @BeforeEach
    void setUp() {
        tasks.deleteAll();
        points.deleteAll();
        members.deleteAll();
        sarah = members.save(TestUsers.member("Sarah", Role.ADMINISTRATOR));
        emma = members.save(TestUsers.member("Emma", Role.JUGENDLICHER));
        lucas = members.save(TestUsers.member("Lucas", Role.KIND));
        lily = members.save(TestUsers.member("Lily", Role.KIND));
        oma = members.save(TestUsers.member("Oma", Role.GAST));
    }

    private static String bonus(String title, int pointValue, boolean repeatable) {
        return """
                {"title": "%s", "bonus": true, "repeatable": %s, "points": %d, "priority": "medium",
                 "category": "chores"}
                """.formatted(title, repeatable, pointValue);
    }

    private String create(String body) throws Exception {
        String response = mvc.perform(post("/api/tasks").with(as(sarah)).contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    private void markDone(FamilyMember who, String id) throws Exception {
        mvc.perform(patch("/api/tasks/" + id + "/status").with(as(who)).contentType(APPLICATION_JSON)
                        .content("{\"status\": \"done\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void onlyParentsCreateBonusTasksWithPointsAndWithoutPersonOrDueDate() throws Exception {
        mvc.perform(post("/api/tasks").with(as(sarah)).contentType(APPLICATION_JSON).content(bonus("Auto waschen", 30, true)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.bonus").value(true))
                .andExpect(jsonPath("$.repeatable").value(true))
                .andExpect(jsonPath("$.assigneeId").doesNotExist())
                .andExpect(jsonPath("$.dueDate").doesNotExist());

        mvc.perform(post("/api/tasks").with(as(sarah)).contentType(APPLICATION_JSON).content(bonus("Umsonst", 0, false)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.points").value("Bonus-Aufgaben brauchen Punkte"));
        mvc.perform(post("/api/tasks").with(as(emma)).contentType(APPLICATION_JSON).content(bonus("Selbst", 10, false)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/tasks").with(as(lucas)).contentType(APPLICATION_JSON).content(bonus("Selbst", 10, false)))
                .andExpect(status().isForbidden());
    }

    @Test
    void openBonusTasksAreVisibleToKidsAndTeensButNotToGuests() throws Exception {
        create(bonus("Garage aufräumen", 40, false));

        mvc.perform(get("/api/tasks").with(as(lucas))).andExpect(jsonPath("$[*].title", hasItem("Garage aufräumen")));
        mvc.perform(get("/api/tasks").with(as(emma))).andExpect(jsonPath("$[*].title", hasItem("Garage aufräumen")));
        mvc.perform(get("/api/tasks").with(as(oma))).andExpect(jsonPath("$[*].title", not(hasItem("Garage aufräumen"))));
    }

    @Test
    void firstChildToClaimGetsTheTaskAndThePoints() throws Exception {
        String id = create(bonus("Garage aufräumen", 40, false));

        mvc.perform(patch("/api/tasks/" + id + "/status").with(as(sarah)).contentType(APPLICATION_JSON)
                        .content("{\"status\": \"done\"}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Eine Bonus-Aufgabe muss erst jemand übernehmen."));

        mvc.perform(post("/api/tasks/" + id + "/claim").with(as(lucas)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assigneeId").value(lucas.id()))
                .andExpect(jsonPath("$.status").value("inprogress"));
        mvc.perform(get("/api/tasks").with(as(lily))).andExpect(jsonPath("$[*].title", not(hasItem("Garage aufräumen"))));
        mvc.perform(post("/api/tasks/" + id + "/claim").with(as(emma)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Diese Bonus-Aufgabe hat schon jemand übernommen."));
        mvc.perform(post("/api/tasks/" + id + "/claim").with(as(sarah))).andExpect(status().isForbidden());

        markDone(lucas, id);
        mvc.perform(post("/api/tasks/" + id + "/confirm").with(as(sarah))).andExpect(status().isOk());
        assertThat(points.findAll()).singleElement().satisfies(p -> {
            assertThat(p.memberId()).isEqualTo(lucas.id());
            assertThat(p.amount()).isEqualTo(40);
        });
        // einmalig: keine neue Runde
        assertThat(tasks.findAll()).filteredOn(Task::isOpenBonus).isEmpty();
    }

    @Test
    void repeatableBonusTaskIsOpenAgainAfterConfirmation() throws Exception {
        String id = create(bonus("Auto waschen", 30, true));
        mvc.perform(post("/api/tasks/" + id + "/claim").with(as(emma))).andExpect(status().isOk());
        markDone(emma, id);
        mvc.perform(post("/api/tasks/" + id + "/confirm").with(as(sarah))).andExpect(status().isOk());

        assertThat(tasks.findAll()).filteredOn(Task::isOpenBonus).singleElement().satisfies(t -> {
            assertThat(t.title()).isEqualTo("Auto waschen");
            assertThat(t.points()).isEqualTo(30);
            assertThat(t.repeatable()).isTrue();
            assertThat(t.status()).isEqualTo(TaskStatus.TODO);
        });
        mvc.perform(get("/api/tasks").with(as(lily))).andExpect(jsonPath("$[*].title", hasItem("Auto waschen")));
    }

    @Test
    void claimedBonusTaskCanBeReturnedUntilItIsDone() throws Exception {
        String id = create(bonus("Fenster putzen", 25, false));
        mvc.perform(post("/api/tasks/" + id + "/claim").with(as(lucas))).andExpect(status().isOk());

        mvc.perform(post("/api/tasks/" + id + "/release").with(as(emma)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Zurückgeben darf nur, wer die Bonus-Aufgabe übernommen hat."));
        mvc.perform(post("/api/tasks/" + id + "/release").with(as(lucas)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assigneeId").doesNotExist())
                .andExpect(jsonPath("$.status").value("todo"));

        mvc.perform(post("/api/tasks/" + id + "/claim").with(as(lily))).andExpect(status().isOk());
        markDone(lily, id);
        mvc.perform(post("/api/tasks/" + id + "/release").with(as(lily)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Erledigte Bonus-Aufgaben lassen sich nicht mehr zurückgeben."));
    }

    @Test
    void editingKeepsWhoClaimedItAndChildrenCannotEdit() throws Exception {
        String id = create(bonus("Rasen mähen", 35, false));
        mvc.perform(post("/api/tasks/" + id + "/claim").with(as(lucas))).andExpect(status().isOk());

        mvc.perform(put("/api/tasks/" + id).with(as(sarah)).contentType(APPLICATION_JSON).content(bonus("Rasen mähen (vorne)", 45, true)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Rasen mähen (vorne)"))
                .andExpect(jsonPath("$.assigneeId").value(lucas.id()))
                .andExpect(jsonPath("$.bonus").value(true));
        mvc.perform(put("/api/tasks/" + id).with(as(lucas)).contentType(APPLICATION_JSON).content(bonus("Mehr Punkte", 500, false)))
                .andExpect(status().isForbidden());
    }
}
