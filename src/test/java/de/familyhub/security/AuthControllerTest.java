package de.familyhub.security;

import static de.familyhub.testsupport.TestUsers.as;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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

import de.familyhub.calendar.CalendarEventRepository;
import de.familyhub.family.FamilyMember;
import de.familyhub.family.FamilyMemberRepository;
import de.familyhub.permission.Role;
import de.familyhub.testsupport.TestUsers;

@SpringBootTest
@AutoConfigureMockMvc
class AuthControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private FamilyMemberRepository members;

    @Autowired
    private CalendarEventRepository events;

    @BeforeEach
    void cleanDatabase() {
        events.deleteAll();
        members.deleteAll();
    }

    private static final String SETUP_JSON = """
            {"name": "Sarah", "color": "#2563EB", "username": "sarah", "password": "geheim123"}
            """;

    @Test
    void setupCreatesFirstAdministratorOnlyOnce() throws Exception {
        mvc.perform(get("/api/auth/status")).andExpect(jsonPath("$.setupRequired").value(true));

        mvc.perform(post("/api/auth/setup").with(csrf()).contentType(APPLICATION_JSON).content(SETUP_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.role").value("administrator"))
                .andExpect(jsonPath("$.passwordHash").doesNotExist());

        mvc.perform(get("/api/auth/status")).andExpect(jsonPath("$.setupRequired").value(false));
        mvc.perform(post("/api/auth/setup").with(csrf()).contentType(APPLICATION_JSON).content(SETUP_JSON))
                .andExpect(status().isConflict());
    }

    @Test
    void loginWithCorrectPasswordReturnsOwnData() throws Exception {
        members.save(TestUsers.member("Emma", Role.KIND));

        mvc.perform(post("/api/auth/login").with(csrf())
                        .param("username", "emma").param("password", TestUsers.PASSWORD))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Emma"))
                .andExpect(jsonPath("$.username").value("emma"));
    }

    @Test
    void loginWithWrongPasswordIsRejected() throws Exception {
        members.save(TestUsers.member("Emma", Role.KIND));

        mvc.perform(post("/api/auth/login").with(csrf()).param("username", "emma").param("password", "falsch"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Anmeldung fehlgeschlagen"));
    }

    @Test
    void apiRequiresLogin() throws Exception {
        mvc.perform(get("/api/events"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.title").value("Nicht angemeldet"));
    }

    @Test
    void changesWithoutCsrfTokenAreRejected() throws Exception {
        FamilyMember sarah = members.save(TestUsers.member("Sarah", Role.ADMINISTRATOR));

        mvc.perform(put("/api/auth/password").with(user(new FamilyUserDetails(sarah)))
                        .contentType(APPLICATION_JSON)
                        .content("{\"currentPassword\": \"x\", \"newPassword\": \"neuesPasswort\"}"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value(containsString("CSRF")));
    }

    @Test
    void meReturnsLoggedInPerson() throws Exception {
        FamilyMember lily = members.save(TestUsers.member("Lily", Role.KIND));

        mvc.perform(get("/api/auth/me").with(as(lily)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(lily.id()))
                .andExpect(jsonPath("$.effectiveRole").value("kind"))
                .andExpect(jsonPath("$.permissions[?(@.module == 'kalender' && @.action == 'ansehen')].scope")
                        .value(containsInAnyOrder("eigen", "familie")))
                .andExpect(jsonPath("$.permissions[?(@.module == 'kalender' && @.action == 'erstellen')]").isEmpty());
    }

    @Test
    void passwordChangeRequiresCurrentPassword() throws Exception {
        FamilyMember emma = members.save(TestUsers.member("Emma", Role.KIND));

        mvc.perform(put("/api/auth/password").with(as(emma)).contentType(APPLICATION_JSON)
                        .content("{\"currentPassword\": \"falsch\", \"newPassword\": \"neuesPasswort\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.currentPassword").value("Das aktuelle Passwort ist falsch"));

        mvc.perform(put("/api/auth/password").with(as(emma)).contentType(APPLICATION_JSON)
                        .content("{\"currentPassword\": \"" + TestUsers.PASSWORD + "\", \"newPassword\": \"neuesPasswort\"}"))
                .andExpect(status().isNoContent());

        mvc.perform(post("/api/auth/login").with(csrf()).param("username", "emma").param("password", "neuesPasswort"))
                .andExpect(status().isOk());
    }

    @Test
    void logoutEndsTheSession() throws Exception {
        FamilyMember emma = members.save(TestUsers.member("Emma", Role.KIND));

        mvc.perform(post("/api/auth/logout").with(as(emma))).andExpect(status().isNoContent());
    }
}
