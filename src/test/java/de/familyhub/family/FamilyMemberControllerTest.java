package de.familyhub.family;

import static de.familyhub.testsupport.TestUsers.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;

import de.familyhub.calendar.CalendarEvent;
import de.familyhub.calendar.CalendarEventRepository;
import de.familyhub.calendar.EventCategory;
import de.familyhub.permission.Action;
import de.familyhub.permission.Module;
import de.familyhub.permission.Permission;
import de.familyhub.permission.Role;
import de.familyhub.permission.Scope;
import de.familyhub.task.Task;
import de.familyhub.task.TaskCategory;
import de.familyhub.task.TaskPriority;
import de.familyhub.task.TaskRepository;
import de.familyhub.testsupport.TestUsers;

@SpringBootTest
@AutoConfigureMockMvc
class FamilyMemberControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private FamilyMemberRepository members;

    @Autowired
    private CalendarEventRepository events;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private TaskRepository tasks;

    private FamilyMember sarah;

    @BeforeEach
    void setUp() {
        events.deleteAll();
        members.deleteAll();
        sarah = members.save(TestUsers.member("Sarah", Role.ADMINISTRATOR));
    }

    private static String json(String name, String username, String password, String role) {
        String passwordJson = password == null ? "null" : "\"" + password + "\"";
        return """
                {"name": "%s", "color": "#EC4899", "username": "%s", "password": %s, "role": "%s",
                 "birthDate": "2018-01-30", "roleFixed": false}
                """.formatted(name, username, passwordJson, role);
    }

    @Test
    void administratorCreatesMemberWithHashedPassword() throws Exception {
        mvc.perform(post("/api/members").with(as(sarah)).contentType(APPLICATION_JSON)
                        .content(json("Lily", "lily", "geheim123", "kind")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern(".*/api/members/[0-9a-f]{24}")))
                .andExpect(jsonPath("$.username").value("lily"))
                .andExpect(jsonPath("$.role").value("kind"))
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        FamilyMember lily = members.findByUsername("lily").orElseThrow();
        assertThat(passwordEncoder.matches("geheim123", lily.passwordHash())).isTrue();
    }

    @Test
    void optionalFieldsMayBeOmitted() throws Exception {
        mvc.perform(post("/api/members").with(as(sarah)).contentType(APPLICATION_JSON).content("""
                        {"name": "Oma", "color": "#64748B", "username": "oma", "password": "geheim123", "role": "gast"}
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.roleFixed").value(false))
                .andExpect(jsonPath("$.birthDate").doesNotExist());
    }

    @Test
    void onlyAdministratorsMayManageMembers() throws Exception {
        FamilyMember emma = members.save(TestUsers.member("Emma", Role.JUGENDLICHER));

        mvc.perform(post("/api/members").with(as(emma)).contentType(APPLICATION_JSON)
                        .content(json("Lily", "lily", "geheim123", "kind")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.title").value("Keine Berechtigung"));
        mvc.perform(delete("/api/members/" + sarah.id()).with(as(emma))).andExpect(status().isForbidden());
    }

    @Test
    void passwordIsRequiredWhenCreating() throws Exception {
        mvc.perform(post("/api/members").with(as(sarah)).contentType(APPLICATION_JSON)
                        .content(json("Lily", "lily", null, "kind")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.password").value("Passwort ist Pflicht"));
    }

    @Test
    void usernameMustBeUnique() throws Exception {
        mvc.perform(post("/api/members").with(as(sarah)).contentType(APPLICATION_JSON)
                        .content(json("Sarah Zwei", "sarah", "geheim123", "gast")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.username").value("Benutzername ist bereits vergeben"));
    }

    @Test
    void kiAgentRoleCannotBeAssigned() throws Exception {
        mvc.perform(post("/api/members").with(as(sarah)).contentType(APPLICATION_JSON)
                        .content(json("Robo", "robo", "geheim123", "ki_agent")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.role").value(containsString("KI-Agent")));
    }

    @Test
    void familyLimitsAreEnforced() throws Exception {
        members.save(TestUsers.member("Mike", Role.ADMINISTRATOR));
        mvc.perform(post("/api/members").with(as(sarah)).contentType(APPLICATION_JSON)
                        .content(json("Opa", "opa", "geheim123", "administrator")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.role").value("Es gibt bereits 2 Administratoren"));

        members.save(TestUsers.member("Emma", Role.JUGENDLICHER));
        for (String child : new String[] {"Kind1", "Kind2", "Kind3", "Kind4"}) {
            members.save(TestUsers.member(child, Role.KIND));
        }
        mvc.perform(post("/api/members").with(as(sarah)).contentType(APPLICATION_JSON)
                        .content(json("Kind Fünf", "kind5", "geheim123", "kind")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.role").value("Es gibt bereits 5 Kinder oder Jugendliche"));

        mvc.perform(post("/api/members").with(as(sarah)).contentType(APPLICATION_JSON)
                        .content(json("Oma", "oma", "geheim123", "gast")))
                .andExpect(status().isCreated());
    }

    @Test
    void lastAdministratorCannotBeRemovedOrDemoted() throws Exception {
        mvc.perform(delete("/api/members/" + sarah.id()).with(as(sarah)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("mindestens ein Administrator")));
        mvc.perform(put("/api/members/" + sarah.id()).with(as(sarah)).contentType(APPLICATION_JSON)
                        .content(json("Sarah", "sarah", null, "gast")))
                .andExpect(status().isConflict());
    }

    @Test
    void updateKeepsPasswordWhenNoneIsSent() throws Exception {
        FamilyMember lily = members.save(TestUsers.member("Lily", Role.KIND));

        mvc.perform(put("/api/members/" + lily.id()).with(as(sarah)).contentType(APPLICATION_JSON)
                        .content(json("Lily M.", "lily", null, "kind")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Lily M."));

        assertThat(members.findById(lily.id()).orElseThrow().passwordHash())
                .isEqualTo(lily.passwordHash());
    }

    @Test
    void privateDetailsAreOnlyVisibleToAdministratorsAndThePersonItself() throws Exception {
        FamilyMember emma = members.save(TestUsers.member("Emma", Role.KIND, LocalDate.of(2010, 2, 14)));
        FamilyMember oma = members.save(TestUsers.member("Oma", Role.GAST));

        mvc.perform(get("/api/members/" + emma.id()).with(as(oma)))
                .andExpect(jsonPath("$.name").value("Emma"))
                .andExpect(jsonPath("$.username").doesNotExist())
                .andExpect(jsonPath("$.birthDate").doesNotExist());
        mvc.perform(get("/api/members/" + emma.id()).with(as(emma)))
                .andExpect(jsonPath("$.username").value("emma"));
        mvc.perform(get("/api/members/" + emma.id()).with(as(sarah)))
                .andExpect(jsonPath("$.birthDate").value("2010-02-14"))
                .andExpect(jsonPath("$.role").value("kind"))
                .andExpect(jsonPath("$.effectiveRole").value("jugendlicher"));
    }

    @Test
    void administratorGrantsSinglePermissionsThatOnlyAdministratorsSee() throws Exception {
        FamilyMember lily = members.save(TestUsers.member("Lily", Role.KIND));
        FamilyMember emma = members.save(TestUsers.member("Emma", Role.JUGENDLICHER));

        mvc.perform(put("/api/members/" + lily.id()).with(as(sarah)).contentType(APPLICATION_JSON).content("""
                        {"name": "Lily", "color": "#EC4899", "username": "lily", "role": "kind",
                         "extraPermissions": [{"module": "einkauf", "action": "bearbeiten", "scope": "familie"}]}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.extraPermissions[0].module").value("einkauf"));

        mvc.perform(get("/api/auth/me").with(as(lily)))
                .andExpect(jsonPath("$.permissions[?(@.module == 'einkauf' && @.action == 'bearbeiten')]").isNotEmpty());
        mvc.perform(get("/api/members/" + lily.id()).with(as(emma)))
                .andExpect(jsonPath("$.extraPermissions").doesNotExist());
    }

    @Test
    void memberManagementAloneGivesNoWayToMoreRights() throws Exception {
        // Emma darf per Einzelrecht Mitglieder verwalten, aber keine Rollen und Rechte vergeben
        FamilyMember emma = members.save(new FamilyMember(null, "Emma", "#8B5CF6", "emma", "{noop}x",
                Role.JUGENDLICHER, null, false,
                Set.of(Permission.of(Module.FAMILIE, Action.VERWALTEN, Scope.FAMILIE)), Set.of()));
        FamilyMember lucas = members.save(TestUsers.member("Lucas", Role.KIND, LocalDate.of(2014, 5, 3)));
        String lucasJson = """
                {"name": "%s", "color": "#F97316", "username": "%s", "password": %s, "role": "kind",
                 "birthDate": "%s", "roleFixed": false}
                """;

        mvc.perform(put("/api/members/" + lucas.id()).with(as(emma)).contentType(APPLICATION_JSON)
                        .content(lucasJson.formatted("Luca", "lucas", "null", "2014-05-03")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Luca"));

        mvc.perform(put("/api/members/" + lucas.id()).with(as(emma)).contentType(APPLICATION_JSON)
                        .content(lucasJson.formatted("Luca", "lucas", "null", "2010-05-03")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Nur Administratoren dürfen Rollen und Rechte vergeben."));
        mvc.perform(put("/api/members/" + lucas.id()).with(as(emma)).contentType(APPLICATION_JSON)
                        .content(lucasJson.formatted("Luca", "lucas", "\"uebernommen1\"", "2014-05-03")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value(containsString("Passwort anderer")));
        mvc.perform(put("/api/members/" + lucas.id()).with(as(emma)).contentType(APPLICATION_JSON)
                        .content(lucasJson.formatted("Luca", "luca", "null", "2014-05-03")))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/members/" + emma.id()).with(as(emma)).contentType(APPLICATION_JSON)
                        .content(json("Emma", "emma", null, "administrator")))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/members/" + sarah.id()).with(as(emma)).contentType(APPLICATION_JSON).content("""
                        {"name": "Sarah", "color": "#2563EB", "username": "sarah", "role": "administrator"}
                        """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Nur Administratoren dürfen Administratoren ändern."));
        mvc.perform(delete("/api/members/" + sarah.id()).with(as(emma))).andExpect(status().isForbidden());
        mvc.perform(post("/api/members").with(as(emma)).contentType(APPLICATION_JSON)
                        .content(json("Lily", "lily", "geheim123", "kind")))
                .andExpect(status().isForbidden());

        FamilyMember unchanged = members.findById(lucas.id()).orElseThrow();
        assertThat(unchanged.passwordHash()).isEqualTo(lucas.passwordHash());
        assertThat(unchanged.birthDate()).isEqualTo(LocalDate.of(2014, 5, 3));
        assertThat(members.findById(sarah.id())).isPresent();
    }

    @Test
    void teenagerCannotReadMembersWhenPermissionIsRevoked() throws Exception {
        FamilyMember emma = members.save(new FamilyMember(null, "Emma", "#8B5CF6", "emma", "{noop}x",
                Role.JUGENDLICHER, null, false, Set.of(),
                Set.of(Permission.of(Module.FAMILIE, Action.ANSEHEN, Scope.FAMILIE))));

        mvc.perform(get("/api/members").with(as(emma))).andExpect(status().isForbidden());
    }

    @Test
    void deleteIsRefusedWhileMemberHasEvents() throws Exception {
        FamilyMember lucas = members.save(TestUsers.member("Lucas", Role.KIND));
        LocalDateTime start = LocalDateTime.of(2026, 9, 25, 10, 0);
        events.save(new CalendarEvent(null, "Basketball", start, start.plusHours(2), lucas.id(),
                EventCategory.SPORTS, null, null));

        mvc.perform(delete("/api/members/" + lucas.id()).with(as(sarah)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("einen Termin")));
    }

    @Test
    void deleteIsRefusedWhileMemberHasTasks() throws Exception {
        FamilyMember lucas = members.save(TestUsers.member("Lucas", Role.KIND));
        Task task = tasks.save(new Task(null, "Müll rausbringen", null, lucas.id(), LocalDate.of(2026, 9, 30),
                TaskPriority.HIGH, TaskCategory.CHORES, 15));

        mvc.perform(delete("/api/members/" + lucas.id()).with(as(sarah)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("eine Aufgabe")));
        tasks.delete(task);
    }

    @Test
    void unknownMemberReturns404() throws Exception {
        mvc.perform(get("/api/members/000000000000000000000000").with(as(sarah)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Nicht gefunden"));
    }
}
