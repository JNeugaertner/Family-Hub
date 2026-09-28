package de.familyhub.task;

import static de.familyhub.testsupport.TestUsers.as;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import de.familyhub.family.FamilyMember;
import de.familyhub.family.FamilyMemberRepository;
import de.familyhub.permission.Action;
import de.familyhub.permission.Module;
import de.familyhub.permission.Permission;
import de.familyhub.permission.Role;
import de.familyhub.permission.Scope;
import de.familyhub.testsupport.TestUsers;

// Rechte an Aufgaben je Rolle: Kinder sehen und haken nur eigene ab, Jugendliche verwalten selbst angelegte
// Aufgaben ohne Punkte, Gäste sehen keine Aufgaben, Punkte vergeben nur Administratoren.
@SpringBootTest
@AutoConfigureMockMvc
class TaskRoleTest {

    private static final LocalDate DUE = LocalDate.of(2026, 9, 30);

    @Autowired
    private MockMvc mvc;

    @Autowired
    private TaskRepository tasks;

    @Autowired
    private FamilyMemberRepository members;

    private FamilyMember sarah;
    private FamilyMember emma;
    private FamilyMember lucas;
    private FamilyMember oma;
    private Task lucasTask;
    private Task emmaTaskFromSarah;

    @BeforeEach
    void setUp() {
        tasks.deleteAll();
        members.deleteAll();
        sarah = members.save(TestUsers.member("Sarah", Role.ADMINISTRATOR));
        emma = members.save(TestUsers.member("Emma", Role.JUGENDLICHER));
        lucas = members.save(TestUsers.member("Lucas", Role.KIND));
        oma = members.save(TestUsers.member("Oma", Role.GAST));
        lucasTask = save("Müll rausbringen", lucas, 15, sarah);
        emmaTaskFromSarah = save("Zimmer aufräumen", emma, 20, sarah);
        save("Garage aufräumen", sarah, 0, sarah);
    }

    private Task save(String title, FamilyMember assignee, int points, FamilyMember creator) {
        return tasks.save(new Task(null, title, null, assignee.id(), DUE, TaskPriority.MEDIUM, TaskCategory.CHORES,
                points, TaskStatus.TODO, creator.id(), null, null));
    }

    private static String json(String title, FamilyMember assignee, int points) {
        return """
                {"title": "%s", "assigneeId": "%s", "dueDate": "2026-09-30", "priority": "medium",
                 "category": "school", "points": %d}
                """.formatted(title, assignee.id(), points);
    }

    private static String statusJson(String status) {
        return "{\"status\": \"" + status + "\"}";
    }

    @Test
    void childSeesOnlyOwnTasksAndMayOnlyTickThemOff() throws Exception {
        mvc.perform(get("/api/tasks").with(as(lucas)))
                .andExpect(jsonPath("$[*].title", containsInAnyOrder("Müll rausbringen")));
        mvc.perform(get("/api/tasks/" + emmaTaskFromSarah.id()).with(as(lucas))).andExpect(status().isNotFound());

        mvc.perform(patch("/api/tasks/" + lucasTask.id() + "/status").with(as(lucas)).contentType(APPLICATION_JSON)
                        .content(statusJson("done")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("done"));

        mvc.perform(post("/api/tasks").with(as(lucas)).contentType(APPLICATION_JSON).content(json("Lego", lucas, 0)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Keine Berechtigung, Aufgaben anzulegen."));
        mvc.perform(put("/api/tasks/" + lucasTask.id()).with(as(lucas)).contentType(APPLICATION_JSON)
                        .content(json("Müll später", lucas, 15)))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/tasks/" + lucasTask.id()).with(as(lucas))).andExpect(status().isForbidden());
    }

    @Test
    void teenagerSeesAllTasksAndManagesOwnTasksWithoutPoints() throws Exception {
        mvc.perform(get("/api/tasks").with(as(emma)))
                .andExpect(jsonPath("$[*].title",
                        containsInAnyOrder("Müll rausbringen", "Zimmer aufräumen", "Garage aufräumen")));

        String location = mvc.perform(post("/api/tasks").with(as(emma)).contentType(APPLICATION_JSON)
                        .content(json("Referat vorbereiten", emma, 0)))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getHeader("Location");
        String ownId = location.substring(location.lastIndexOf('/') + 1);

        mvc.perform(put("/api/tasks/" + ownId).with(as(emma)).contentType(APPLICATION_JSON)
                        .content(json("Referat Biologie", emma, 0)))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/tasks/" + ownId).with(as(emma))).andExpect(status().isNoContent());
    }

    @Test
    void teenagerMayNotSetPointsOrCreateTasksForOthers() throws Exception {
        mvc.perform(post("/api/tasks").with(as(emma)).contentType(APPLICATION_JSON)
                        .content(json("Referat", emma, 50)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Punkte für Aufgaben vergeben nur Administratoren."));
        mvc.perform(post("/api/tasks").with(as(emma)).contentType(APPLICATION_JSON)
                        .content(json("Für Lucas", lucas, 0)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Du darfst nur deine eigenen Aufgaben anlegen."));
    }

    @Test
    void tasksAssignedByParentsCanOnlyBeTickedOff() throws Exception {
        mvc.perform(patch("/api/tasks/" + emmaTaskFromSarah.id() + "/status").with(as(emma))
                        .contentType(APPLICATION_JSON).content(statusJson("inprogress")))
                .andExpect(status().isOk());

        mvc.perform(put("/api/tasks/" + emmaTaskFromSarah.id()).with(as(emma)).contentType(APPLICATION_JSON)
                        .content(json("Zimmer später", emma, 20)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Aufgaben, die dir jemand anderes zugewiesen hat, kannst du nur abhaken."));
        mvc.perform(delete("/api/tasks/" + emmaTaskFromSarah.id()).with(as(emma))).andExpect(status().isForbidden());
    }

    @Test
    void teenagerMayNotTickOffTasksOfOthers() throws Exception {
        mvc.perform(patch("/api/tasks/" + lucasTask.id() + "/status").with(as(emma)).contentType(APPLICATION_JSON)
                        .content(statusJson("done")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Du darfst nur deine eigenen Aufgaben abhaken."));
    }

    @Test
    void guestSeesNoTasks() throws Exception {
        mvc.perform(get("/api/tasks").with(as(oma))).andExpect(jsonPath("$", empty()));
        mvc.perform(get("/api/tasks/" + lucasTask.id()).with(as(oma))).andExpect(status().isNotFound());
    }

    @Test
    void administratorManagesAllTasksAndPoints() throws Exception {
        mvc.perform(put("/api/tasks/" + lucasTask.id()).with(as(sarah)).contentType(APPLICATION_JSON)
                        .content(json("Müll und Altpapier", lucas, 25)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points").value(25));
        mvc.perform(delete("/api/tasks/" + emmaTaskFromSarah.id()).with(as(sarah))).andExpect(status().isNoContent());
    }

    @Test
    void revokedRightPreventsTickingOff() throws Exception {
        FamilyMember lucasWithoutTicking = members.save(new FamilyMember(lucas.id(), lucas.name(), lucas.color(),
                lucas.username(), lucas.passwordHash(), lucas.role(), lucas.birthDate(), lucas.roleFixed(), Set.of(),
                Set.of(Permission.of(Module.AUFGABEN, Action.BEARBEITEN, Scope.EIGEN))));

        mvc.perform(patch("/api/tasks/" + lucasTask.id() + "/status").with(as(lucasWithoutTicking))
                        .contentType(APPLICATION_JSON).content(statusJson("done")))
                .andExpect(status().isForbidden());
    }
}
