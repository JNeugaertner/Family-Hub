package de.familyhub.points;

import static de.familyhub.testsupport.TestUsers.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import de.familyhub.family.FamilyMember;
import de.familyhub.family.FamilyMemberRepository;
import de.familyhub.permission.Role;
import de.familyhub.task.Task;
import de.familyhub.task.TaskCategory;
import de.familyhub.task.TaskPriority;
import de.familyhub.task.TaskRepository;
import de.familyhub.task.TaskStatus;
import de.familyhub.testsupport.TestUsers;

// User Story 4.2: Nach bestätigter Erledigung gibt es Punkte, genau einmal; Punktestand und Historie sind je nach
// Rolle sichtbar.
@SpringBootTest
@AutoConfigureMockMvc
class TaskConfirmationTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private TaskRepository tasks;

    @Autowired
    private PointEntryRepository points;

    @Autowired
    private FamilyMemberRepository members;

    private FamilyMember sarah;
    private FamilyMember emma;
    private FamilyMember lucas;
    private FamilyMember oma;

    @BeforeEach
    void setUp() {
        tasks.deleteAll();
        points.deleteAll();
        members.deleteAll();
        sarah = members.save(TestUsers.member("Sarah", Role.ADMINISTRATOR));
        emma = members.save(TestUsers.member("Emma", Role.JUGENDLICHER));
        lucas = members.save(TestUsers.member("Lucas", Role.KIND));
        oma = members.save(TestUsers.member("Oma", Role.GAST));
    }

    private Task task(FamilyMember assignee, int points, TaskStatus status) {
        return tasks.save(new Task(null, "Zimmer aufräumen", null, assignee.id(), LocalDate.of(2026, 9, 30),
                TaskPriority.MEDIUM, TaskCategory.CHORES, points).withStatus(status));
    }

    @Test
    void confirmedTaskCreditsPointsOnceAndIsLocked() throws Exception {
        Task task = task(lucas, 20, TaskStatus.TODO);
        mvc.perform(patch("/api/tasks/" + task.id() + "/status").with(as(lucas)).contentType(APPLICATION_JSON)
                        .content("{\"status\": \"done\"}"))
                .andExpect(status().isOk());

        mvc.perform(post("/api/tasks/" + task.id() + "/confirm").with(as(sarah)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("confirmed"))
                .andExpect(jsonPath("$.confirmedBy").value(sarah.id()))
                .andExpect(jsonPath("$.confirmedAt").exists());

        mvc.perform(get("/api/points").with(as(lucas)))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].memberId").value(lucas.id()))
                .andExpect(jsonPath("$[0].points").value(20));
        mvc.perform(get("/api/points/history").with(as(lucas)))
                .andExpect(jsonPath("$[0].amount").value(20))
                .andExpect(jsonPath("$[0].reason").value("Aufgabe erledigt: Zimmer aufräumen"))
                .andExpect(jsonPath("$[0].taskId").value(task.id()));

        mvc.perform(post("/api/tasks/" + task.id() + "/confirm").with(as(sarah)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Die Aufgabe ist bereits bestätigt, die Punkte sind gutgeschrieben."));
        mvc.perform(patch("/api/tasks/" + task.id() + "/status").with(as(lucas)).contentType(APPLICATION_JSON)
                        .content("{\"status\": \"todo\"}"))
                .andExpect(status().isConflict());
        mvc.perform(put("/api/tasks/" + task.id()).with(as(sarah)).contentType(APPLICATION_JSON).content("""
                        {"title": "Mehr Punkte", "assigneeId": "%s", "dueDate": "2026-09-30", "priority": "medium",
                         "category": "chores", "points": 100}
                        """.formatted(lucas.id())))
                .andExpect(status().isConflict());
        assertThat(points.count()).isEqualTo(1);
    }

    @Test
    void onlyDoneTasksWithPointsCanBeConfirmed() throws Exception {
        Task open = task(lucas, 20, TaskStatus.IN_PROGRESS);
        Task withoutPoints = task(lucas, 0, TaskStatus.DONE);

        mvc.perform(post("/api/tasks/" + open.id() + "/confirm").with(as(sarah)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Nur erledigte Aufgaben mit Punkten warten auf eine Bestätigung."));
        mvc.perform(post("/api/tasks/" + withoutPoints.id() + "/confirm").with(as(sarah)))
                .andExpect(status().isConflict());
        assertThat(points.count()).isZero();
    }

    @Test
    void administratorCanHandTaskBackWithoutPoints() throws Exception {
        Task task = task(lucas, 20, TaskStatus.DONE);

        mvc.perform(post("/api/tasks/" + task.id() + "/reopen").with(as(sarah)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("inprogress"));
        assertThat(points.count()).isZero();
    }

    @Test
    void onlyAdministratorsConfirmOrHandBack() throws Exception {
        Task lucasTask = task(lucas, 20, TaskStatus.DONE);
        Task emmaTask = task(emma, 20, TaskStatus.DONE);

        mvc.perform(post("/api/tasks/" + lucasTask.id() + "/confirm").with(as(lucas)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail")
                        .value("Nur Administratoren dürfen erledigte Aufgaben bestätigen oder zurückgeben."));
        mvc.perform(post("/api/tasks/" + emmaTask.id() + "/confirm").with(as(emma))).andExpect(status().isForbidden());
        mvc.perform(post("/api/tasks/" + emmaTask.id() + "/reopen").with(as(emma))).andExpect(status().isForbidden());
        assertThat(points.count()).isZero();
    }

    @Test
    void balancesAndHistoryAreVisibleByRole() throws Exception {
        mvc.perform(post("/api/tasks/" + task(lucas, 20, TaskStatus.DONE).id() + "/confirm").with(as(sarah)));
        mvc.perform(post("/api/tasks/" + task(lucas, 5, TaskStatus.DONE).id() + "/confirm").with(as(sarah)));
        mvc.perform(post("/api/tasks/" + task(emma, 30, TaskStatus.DONE).id() + "/confirm").with(as(sarah)));

        mvc.perform(get("/api/points").with(as(emma)))
                .andExpect(jsonPath("$[*].memberId", containsInAnyOrder(sarah.id(), emma.id(), lucas.id(), oma.id())))
                .andExpect(jsonPath("$[?(@.memberId == '" + lucas.id() + "')].points").value(containsInAnyOrder(25)))
                .andExpect(jsonPath("$[?(@.memberId == '" + emma.id() + "')].points").value(containsInAnyOrder(30)));
        mvc.perform(get("/api/points/history?memberId=" + lucas.id()).with(as(emma)))
                .andExpect(jsonPath("$", hasSize(2)));

        mvc.perform(get("/api/points/history?memberId=" + emma.id()).with(as(lucas)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Du darfst nur deine eigenen Punkte sehen."));
        mvc.perform(get("/api/points").with(as(oma)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Keine Berechtigung, Punkte anzusehen."));
    }
}
