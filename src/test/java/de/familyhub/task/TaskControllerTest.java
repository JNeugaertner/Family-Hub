package de.familyhub.task;

import static de.familyhub.testsupport.TestUsers.as;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import de.familyhub.testsupport.TestUsers;

// Grundfunktionen der Aufgaben-API als Administratorin: anlegen, ändern, abhaken, löschen, Validierung.
@SpringBootTest
@AutoConfigureMockMvc
class TaskControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private TaskRepository tasks;

    @Autowired
    private FamilyMemberRepository members;

    private FamilyMember sarah;
    private FamilyMember lucas;

    @BeforeEach
    void setUp() {
        tasks.deleteAll();
        members.deleteAll();
        sarah = members.save(TestUsers.member("Sarah", Role.ADMINISTRATOR));
        lucas = members.save(TestUsers.member("Lucas", Role.KIND));
    }

    private static String json(String title, FamilyMember assignee, String dueDate, int points) {
        return """
                {"title": "%s", "assigneeId": "%s", "dueDate": "%s", "priority": "high", "category": "chores",
                 "points": %d}
                """.formatted(title, assignee.id(), dueDate, points);
    }

    @Test
    void administratorCreatesTaskWithPoints() throws Exception {
        mvc.perform(post("/api/tasks").with(as(sarah)).contentType(APPLICATION_JSON)
                        .content(json("Zimmer aufräumen", lucas, "2026-09-30", 20)))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern(".*/api/tasks/[0-9a-f]{24}")))
                .andExpect(jsonPath("$.status").value("todo"))
                .andExpect(jsonPath("$.points").value(20))
                .andExpect(jsonPath("$.priority").value("high"))
                .andExpect(jsonPath("$.dueDate").value("2026-09-30"))
                .andExpect(jsonPath("$.createdBy").value(sarah.id()));
    }

    @Test
    void pointsAreOptionalAndDefaultToZero() throws Exception {
        mvc.perform(post("/api/tasks").with(as(sarah)).contentType(APPLICATION_JSON).content("""
                        {"title": "Blumen gießen", "assigneeId": "%s", "dueDate": "2026-09-30",
                         "priority": "low", "category": "home"}
                        """.formatted(lucas.id())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.points").value(0));
    }

    @Test
    void invalidInputIsRejectedWithFieldErrors() throws Exception {
        mvc.perform(post("/api/tasks").with(as(sarah)).contentType(APPLICATION_JSON).content("""
                        {"title": " ", "assigneeId": "%s", "priority": "high", "category": "chores", "points": -5}
                        """.formatted(lucas.id())))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.title").value("Titel darf nicht leer sein"))
                .andExpect(jsonPath("$.errors.dueDate").value("Fälligkeit ist Pflicht"))
                .andExpect(jsonPath("$.errors.points").value("Punkte dürfen nicht negativ sein"));

        mvc.perform(post("/api/tasks").with(as(sarah)).contentType(APPLICATION_JSON).content("""
                        {"title": "Unbekannt", "assigneeId": "000000000000000000000000", "dueDate": "2026-09-30",
                         "priority": "high", "category": "chores"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.assigneeId").value("Familienmitglied existiert nicht"));
    }

    @Test
    void updateKeepsStatusAndCreator() throws Exception {
        Task task = tasks.save(new Task(null, "Hausaufgaben", null, lucas.id(), LocalDate.of(2026, 9, 30),
                TaskPriority.HIGH, TaskCategory.SCHOOL, 25).withStatus(TaskStatus.IN_PROGRESS));

        mvc.perform(put("/api/tasks/" + task.id()).with(as(sarah)).contentType(APPLICATION_JSON)
                        .content(json("Mathe-Hausaufgaben", lucas, "2026-10-01", 30)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Mathe-Hausaufgaben"))
                .andExpect(jsonPath("$.points").value(30))
                .andExpect(jsonPath("$.status").value("inprogress"));
    }

    @Test
    void statusChangesViaPatchAndListCanBeFiltered() throws Exception {
        Task task = tasks.save(new Task(null, "Müll rausbringen", null, lucas.id(), LocalDate.of(2026, 9, 28),
                TaskPriority.HIGH, TaskCategory.CHORES, 15));
        tasks.save(new Task(null, "Lesen", null, lucas.id(), LocalDate.of(2026, 9, 29), TaskPriority.LOW,
                TaskCategory.SCHOOL, 10));

        mvc.perform(patch("/api/tasks/" + task.id() + "/status").with(as(sarah)).contentType(APPLICATION_JSON)
                        .content("{\"status\": \"inprogress\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("inprogress"));

        mvc.perform(get("/api/tasks?status=inprogress").with(as(sarah)))
                .andExpect(jsonPath("$[*].title", contains("Müll rausbringen")));
        mvc.perform(get("/api/tasks").with(as(sarah)))
                .andExpect(jsonPath("$[*].title", contains("Müll rausbringen", "Lesen")));
    }

    @Test
    void confirmingIsNotPossibleViaStatusChange() throws Exception {
        Task task = tasks.save(new Task(null, "Müll rausbringen", null, lucas.id(), LocalDate.of(2026, 9, 28),
                TaskPriority.HIGH, TaskCategory.CHORES, 15));

        mvc.perform(patch("/api/tasks/" + task.id() + "/status").with(as(sarah)).contentType(APPLICATION_JSON)
                        .content("{\"status\": \"confirmed\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.status").exists());
    }

    @Test
    void deleteRemovesTaskAndUnknownIdReturns404() throws Exception {
        Task task = tasks.save(new Task(null, "Müll rausbringen", null, lucas.id(), LocalDate.of(2026, 9, 28),
                TaskPriority.HIGH, TaskCategory.CHORES, 15));

        mvc.perform(delete("/api/tasks/" + task.id()).with(as(sarah))).andExpect(status().isNoContent());
        mvc.perform(get("/api/tasks/" + task.id()).with(as(sarah)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Nicht gefunden"));
    }
}
