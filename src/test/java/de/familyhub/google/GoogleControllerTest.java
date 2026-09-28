package de.familyhub.google;

import static de.familyhub.testsupport.TestUsers.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import de.familyhub.calendar.EventCategory;
import de.familyhub.family.FamilyMember;
import de.familyhub.family.FamilyMemberRepository;
import de.familyhub.permission.Role;
import de.familyhub.testsupport.TestUsers;

// Google-Konto verbinden und trennen. Google selbst ist hier nur simuliert (GoogleApi als Mock).
@SpringBootTest
@AutoConfigureMockMvc
class GoogleControllerTest {

    private static final String FRONTEND = "http://localhost:5173";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private FamilyMemberRepository members;

    @Autowired
    private GoogleConnectionRepository connections;

    @Autowired
    private TokenCipher cipher;

    @MockitoBean
    private GoogleApi google;

    private FamilyMember lucas;
    private FamilyMember oma;
    private MockHttpSession session;

    @BeforeEach
    void setUp() {
        connections.deleteAll();
        members.deleteAll();
        members.save(TestUsers.member("Sarah", Role.ADMINISTRATOR));
        lucas = members.save(TestUsers.member("Lucas", Role.KIND));
        oma = members.save(TestUsers.member("Oma", Role.GAST));
        session = new MockHttpSession();
        when(google.authorizationUrl(anyString())).thenAnswer(call -> "https://accounts.test/auth?state="
                + call.getArgument(0));
    }

    private String startConnect(FamilyMember member) throws Exception {
        mvc.perform(post("/api/google/connect").with(as(member)).session(session))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.authorizationUrl").value(org.hamcrest.Matchers.startsWith(
                        "https://accounts.test/auth?state=")));
        return (String) session.getAttribute(GoogleController.STATE_ATTRIBUTE);
    }

    private void googleAnswers(String refreshToken) {
        when(google.exchangeCode("code-123")).thenReturn(new GoogleApi.Tokens("access-1", refreshToken));
        when(google.listCalendars("access-1")).thenReturn(List.of(
                new GoogleApi.CalendarInfo("lucas@example.com", "Lucas", null, true),
                new GoogleApi.CalendarInfo("fussball@group.calendar.google.com", "Fußball", null, null)));
    }

    @Test
    void childConnectsAccountAndTokenIsStoredEncrypted() throws Exception {
        mvc.perform(get("/api/google/status").with(as(lucas)))
                .andExpect(jsonPath("$.configured").value(true))
                .andExpect(jsonPath("$.connected").value(false));

        String state = startConnect(lucas);
        googleAnswers("refresh-geheim");

        mvc.perform(get("/api/google/callback").param("code", "code-123").param("state", state)
                        .with(as(lucas)).session(session))
                .andExpect(status().isFound())
                .andExpect(redirectedUrl(FRONTEND + "?google=verbunden"));

        GoogleConnection connection = connections.findByMemberId(lucas.id()).orElseThrow();
        assertThat(connection.googleEmail()).isEqualTo("lucas@example.com");
        assertThat(connection.encryptedRefreshToken()).isNotEqualTo("refresh-geheim");
        assertThat(cipher.decrypt(connection.encryptedRefreshToken())).isEqualTo("refresh-geheim");
        assertThat(connection.calendars()).containsExactly(
                new GoogleCalendarChoice("lucas@example.com", "Lucas", true, true, EventCategory.APPOINTMENT),
                new GoogleCalendarChoice("fussball@group.calendar.google.com", "Fußball", false, false,
                        EventCategory.APPOINTMENT));

        mvc.perform(get("/api/google/status").with(as(lucas)))
                .andExpect(jsonPath("$.connected").value(true))
                .andExpect(jsonPath("$.email").value("lucas@example.com"))
                .andExpect(jsonPath("$.calendars[0].enabled").value(true))
                .andExpect(jsonPath("$.calendars[0].category").value("appointment"))
                .andExpect(jsonPath("$.encryptedRefreshToken").doesNotExist());
    }

    @Test
    void reconnectKeepsCalendarSelection() throws Exception {
        connections.save(new GoogleConnection(null, lucas.id(), "lucas@example.com", cipher.encrypt("alt"),
                Instant.now(), List.of(new GoogleCalendarChoice("fussball@group.calendar.google.com", "Alt", false,
                        true, EventCategory.SPORTS)), null, null, true));

        String state = startConnect(lucas);
        googleAnswers("refresh-neu");
        mvc.perform(get("/api/google/callback").param("code", "code-123").param("state", state)
                        .with(as(lucas)).session(session))
                .andExpect(redirectedUrl(FRONTEND + "?google=verbunden"));

        GoogleConnection connection = connections.findByMemberId(lucas.id()).orElseThrow();
        assertThat(connections.count()).isEqualTo(1);
        assertThat(connection.needsReconnect()).isFalse();
        assertThat(connection.calendars()).contains(new GoogleCalendarChoice("fussball@group.calendar.google.com",
                "Fußball", false, true, EventCategory.SPORTS));
    }

    @Test
    void callbackWithForeignStateIsRejected() throws Exception {
        startConnect(lucas);

        mvc.perform(get("/api/google/callback").param("code", "code-123").param("state", "fremder-state")
                        .with(as(lucas)).session(session))
                .andExpect(redirectedUrl(FRONTEND + "?google=fehler"));

        verify(google, never()).exchangeCode(anyString());
        assertThat(connections.count()).isZero();
    }

    @Test
    void cancelledAtGoogleLeadsBackWithoutConnection() throws Exception {
        String state = startConnect(lucas);

        mvc.perform(get("/api/google/callback").param("error", "access_denied").param("state", state)
                        .with(as(lucas)).session(session))
                .andExpect(redirectedUrl(FRONTEND + "?google=abgebrochen"));

        assertThat(connections.count()).isZero();
    }

    @Test
    void googleErrorDuringConnectLeadsBackWithError() throws Exception {
        String state = startConnect(lucas);
        when(google.exchangeCode("code-123")).thenThrow(new GoogleException("kaputt", false));

        mvc.perform(get("/api/google/callback").param("code", "code-123").param("state", state)
                        .with(as(lucas)).session(session))
                .andExpect(redirectedUrl(FRONTEND + "?google=fehler"));
    }

    @Test
    void guestMayNotConnect() throws Exception {
        mvc.perform(get("/api/google/status").with(as(oma)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Keine Berechtigung, einen Google-Kalender zu verbinden."));
        mvc.perform(post("/api/google/connect").with(as(oma)).session(session))
                .andExpect(status().isForbidden());
    }

    @Test
    void disconnectRevokesAccessAndDeletesConnection() throws Exception {
        connections.save(new GoogleConnection(null, lucas.id(), "lucas@example.com", cipher.encrypt("refresh-1"),
                Instant.now(), List.of(), null, null, false));

        mvc.perform(delete("/api/google").with(as(lucas))).andExpect(status().isNoContent());

        verify(google).revoke("refresh-1");
        assertThat(connections.count()).isZero();
        mvc.perform(delete("/api/google").with(as(lucas)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("Es ist kein Google-Kalender verbunden."));
    }

    @Test
    void disconnectWorksEvenIfGoogleIsUnreachable() throws Exception {
        connections.save(new GoogleConnection(null, lucas.id(), "lucas@example.com", cipher.encrypt("refresh-1"),
                Instant.now(), List.of(), null, null, false));
        doThrow(new GoogleException("nicht erreichbar", false)).when(google).revoke("refresh-1");

        mvc.perform(delete("/api/google").with(as(lucas))).andExpect(status().isNoContent());

        assertThat(connections.count()).isZero();
    }
}
