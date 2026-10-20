package de.familyhub.google;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

// Die Aufrufe an Google gegen einen simulierten Server: Formulare, Header, Blättern und Fehler.
class GoogleApiTest {

    private static final GoogleProperties PROPERTIES = new GoogleProperties("client-id", "client-secret", "key",
            "http://localhost:8080/api/google/callback", "http://localhost:5173",
            "https://auth.test/auth", "https://auth.test/token", "https://auth.test/revoke",
            "https://api.test/calendar/v3");

    private static final String FAMILYHUB = "fh@group.calendar.google.com";
    private static final String FAMILYHUB_URL = "https://api.test/calendar/v3/calendars/fh%40group.calendar.google.com";

    private MockRestServiceServer server;
    private GoogleApi api;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        api = new GoogleApi(PROPERTIES, builder);
    }

    @Test
    void authorizationUrlAsksForReadOnlyAccessWithRefreshToken() {
        String url = api.authorizationUrl("state-123", false);

        assertThat(url).startsWith("https://auth.test/auth?")
                .contains("client_id=client-id")
                .contains("redirect_uri=http://localhost:8080/api/google/callback")
                .contains("response_type=code")
                .contains("scope=https://www.googleapis.com/auth/calendar.readonly")
                .contains("access_type=offline")
                .contains("prompt=consent")
                .contains("state=state-123");
    }

    @Test
    void authorizationUrlAsksForWriteAccessOnlyWhenWanted() {
        assertThat(api.authorizationUrl("state-123", true)).contains("scope=https://www.googleapis.com/auth/"
                + "calendar.readonly%20https://www.googleapis.com/auth/calendar.app.created");
        assertThat(api.authorizationUrl("state-123", false)).doesNotContain("calendar.app.created");
    }

    @Test
    void tokensTellWhetherWritingWasAllowed() {
        server.expect(requestTo("https://auth.test/token"))
                .andRespond(withSuccess("""
                        {"access_token": "access-1", "refresh_token": "refresh-1", "token_type": "Bearer",
                         "scope": "https://www.googleapis.com/auth/calendar.readonly https://www.googleapis.com/auth/calendar.app.created"}
                        """, MediaType.APPLICATION_JSON));

        GoogleApi.Tokens tokens = api.exchangeCode("code-123");

        assertThat(tokens.grants(GoogleApi.WRITE_SCOPE)).isTrue();
        assertThat(new GoogleApi.Tokens("access-1", "refresh-1", GoogleApi.READ_SCOPE).grants(GoogleApi.WRITE_SCOPE))
                .isFalse();
    }

    @Test
    void exchangeCodeSendsClientCredentialsAsForm() {
        server.expect(requestTo("https://auth.test/token"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(content().formDataContains(Map.of(
                        "client_id", "client-id",
                        "client_secret", "client-secret",
                        "grant_type", "authorization_code",
                        "code", "code-123",
                        "redirect_uri", "http://localhost:8080/api/google/callback")))
                .andRespond(withSuccess("""
                        {"access_token": "access-1", "refresh_token": "refresh-1", "expires_in": 3599,
                         "token_type": "Bearer"}
                        """, MediaType.APPLICATION_JSON));

        GoogleApi.Tokens tokens = api.exchangeCode("code-123");

        assertThat(tokens.accessToken()).isEqualTo("access-1");
        assertThat(tokens.refreshToken()).isEqualTo("refresh-1");
        server.verify();
    }

    @Test
    void expiredRefreshTokenRequiresReconnect() {
        server.expect(requestTo("https://auth.test/token"))
                .andRespond(withBadRequest().contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\": \"invalid_grant\", \"error_description\": \"Token has been expired or revoked.\"}"));

        assertThatThrownBy(() -> api.refreshAccessToken("refresh-1"))
                .isInstanceOfSatisfying(GoogleException.class, e -> assertThat(e.reconnectNeeded()).isTrue());
    }

    @Test
    void disabledCalendarApiIsReportedClearly() {
        server.expect(requestTo("https://api.test/calendar/v3/users/me/calendarList?maxResults=250"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).contentType(MediaType.APPLICATION_JSON).body("""
                        {"error": {"code": 403, "message": "Google Calendar API has not been used in project 123",
                          "errors": [{"reason": "accessNotConfigured"}], "status": "PERMISSION_DENIED"}}
                        """));

        assertThatThrownBy(() -> api.listCalendars("access-1"))
                .isInstanceOf(GoogleException.class)
                .hasMessage("Die Google Calendar API ist im Google-Cloud-Projekt nicht aktiviert.");
    }

    @Test
    void missingCalendarPermissionRequiresReconnect() {
        server.expect(requestTo("https://api.test/calendar/v3/users/me/calendarList?maxResults=250"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).contentType(MediaType.APPLICATION_JSON).body("""
                        {"error": {"code": 403, "message": "Request had insufficient authentication scopes.",
                          "errors": [{"reason": "insufficientPermissions"}], "status": "PERMISSION_DENIED",
                          "details": [{"reason": "ACCESS_TOKEN_SCOPE_INSUFFICIENT"}]}}
                        """));

        assertThatThrownBy(() -> api.listCalendars("access-1"))
                .isInstanceOfSatisfying(GoogleException.class, e -> assertThat(e.reconnectNeeded()).isTrue());
    }

    @Test
    void otherErrorsKeepGooglesMessage() {
        server.expect(requestTo("https://api.test/calendar/v3/users/me/calendarList?maxResults=250"))
                .andRespond(withStatus(HttpStatus.FORBIDDEN).body("{\"error\": {\"message\": \"Rate Limit Exceeded\"}}"));

        assertThatThrownBy(() -> api.listCalendars("access-1"))
                .hasMessageContaining("HTTP 403").hasMessageContaining("Rate Limit Exceeded");
    }

    @Test
    void listCalendarsFollowsAllPages() {
        server.expect(requestTo("https://api.test/calendar/v3/users/me/calendarList?maxResults=250"))
                .andExpect(header("Authorization", "Bearer access-1"))
                .andRespond(withSuccess("""
                        {"items": [{"id": "sarah@example.com", "summary": "Sarah", "primary": true}],
                         "nextPageToken": "seite+2"}
                        """, MediaType.APPLICATION_JSON));
        server.expect(requestTo("https://api.test/calendar/v3/users/me/calendarList?maxResults=250&pageToken=seite%2B2"))
                .andRespond(withSuccess("""
                        {"items": [{"id": "familie@group.calendar.google.com", "summary": "Familie",
                                    "summaryOverride": "Unsere Familie"}]}
                        """, MediaType.APPLICATION_JSON));

        List<GoogleApi.CalendarInfo> calendars = api.listCalendars("access-1");

        assertThat(calendars).extracting(GoogleApi.CalendarInfo::name).containsExactly("Sarah", "Unsere Familie");
        assertThat(calendars).extracting(GoogleApi.CalendarInfo::isPrimary).containsExactly(true, false);
        server.verify();
    }

    @Test
    void listEventsExpandsSeriesInTheTimeWindowAndReadsBothTimeFormats() {
        server.expect(requestTo(startsWith(
                        "https://api.test/calendar/v3/calendars/de.german%23holiday%40group.v.calendar.google.com/events?")))
                .andExpect(queryParam("singleEvents", "true"))
                // Werte kommen kodiert an (: als %3A), Google dekodiert sie wieder
                .andExpect(queryParam("timeMin", "2025-09-28T00%3A00%3A00Z"))
                .andExpect(queryParam("timeMax", "2027-09-28T00%3A00%3A00Z"))
                .andRespond(withSuccess("""
                        {"items": [
                          {"id": "e1", "status": "confirmed", "summary": "Zahnarzt",
                           "start": {"dateTime": "2026-09-29T10:00:00+02:00"},
                           "end": {"dateTime": "2026-09-29T11:00:00+02:00"}, "visibility": "private"},
                          {"id": "e2", "status": "confirmed", "summary": "Tag der Deutschen Einheit",
                           "start": {"date": "2026-10-03"}, "end": {"date": "2026-10-04"}}
                        ]}
                        """, MediaType.APPLICATION_JSON));

        List<GoogleEvent> events = api.listEvents("access-1", "de.german#holiday@group.v.calendar.google.com",
                Instant.parse("2025-09-28T00:00:00Z"), Instant.parse("2027-09-28T00:00:00Z"));

        assertThat(events).hasSize(2);
        assertThat(events.get(0).start().dateTime().toInstant())
                .isEqualTo(OffsetDateTime.parse("2026-09-29T10:00:00+02:00").toInstant());
        assertThat(events.get(0).visibility()).isEqualTo("private");
        assertThat(events.get(1).start().date()).isEqualTo(LocalDate.of(2026, 10, 3));
        assertThat(events.get(1).start().dateTime()).isNull();
        assertThat(events.get(0).familyhubId()).isNull();
        server.verify();
    }

    @Test
    void listEventsReadsTheFamilyHubIdOfEntriesFamilyHubWrote() {
        server.expect(requestTo(startsWith(FAMILYHUB_URL + "/events?")))
                .andRespond(withSuccess("""
                        {"items": [
                          {"id": "g1", "status": "confirmed", "summary": "Arzt",
                           "start": {"dateTime": "2026-10-01T10:00:00+02:00", "timeZone": "Europe/Berlin"},
                           "end": {"dateTime": "2026-10-01T11:00:00+02:00", "timeZone": "Europe/Berlin"},
                           "extendedProperties": {"private": {"familyhubId": "abc"}, "shared": {"x": "y"}}}
                        ]}
                        """, MediaType.APPLICATION_JSON));

        List<GoogleEvent> events = api.listEvents("access-1", FAMILYHUB,
                Instant.parse("2025-09-28T00:00:00Z"), Instant.parse("2027-09-28T00:00:00Z"));

        assertThat(events).singleElement().satisfies(e -> assertThat(e.familyhubId()).isEqualTo("abc"));
    }

    @Test
    void createsTheFamilyHubCalendarAndWritesEntriesAsJson() {
        server.expect(requestTo("https://api.test/calendar/v3/calendars"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer access-1"))
                .andExpect(jsonPath("$.summary").value("FamilyHub"))
                .andExpect(jsonPath("$.timeZone").value("Europe/Berlin"))
                .andRespond(withSuccess("{\"id\": \"fh@group.calendar.google.com\", \"summary\": \"FamilyHub\"}",
                        MediaType.APPLICATION_JSON));
        server.expect(requestTo(FAMILYHUB_URL + "/events"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("Authorization", "Bearer access-1"))
                .andExpect(jsonPath("$.summary").value("Arzt"))
                .andExpect(jsonPath("$.visibility").value("private"))
                .andExpect(jsonPath("$.start.dateTime").value("2026-10-01T10:00:00+02:00"))
                .andExpect(jsonPath("$.start.timeZone").value("Europe/Berlin"))
                .andExpect(jsonPath("$.extendedProperties.private.familyhubId").value("abc"))
                // Leere Felder gehen nicht mit
                .andExpect(jsonPath("$.location").doesNotExist())
                .andRespond(withSuccess("{\"id\": \"g1\"}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(FAMILYHUB_URL + "/events/g1"))
                .andExpect(method(HttpMethod.PUT))
                .andExpect(jsonPath("$.summary").value("Arzt"))
                .andRespond(withSuccess("{\"id\": \"g1\"}", MediaType.APPLICATION_JSON));

        GoogleApi.CalendarInfo calendar = api.createCalendar("access-1", "FamilyHub", "Termine aus FamilyHub",
                "Europe/Berlin");
        GoogleEventInput arzt = new GoogleEventInput("Arzt", null, null, "private",
                new GoogleEventInput.Time("2026-10-01T10:00:00+02:00", "Europe/Berlin"),
                new GoogleEventInput.Time("2026-10-01T11:00:00+02:00", "Europe/Berlin"),
                new GoogleEventInput.ExtendedProperties(Map.of("familyhubId", "abc")));

        assertThat(calendar.id()).isEqualTo(FAMILYHUB);
        assertThat(api.insertEvent("access-1", calendar.id(), arzt).id()).isEqualTo("g1");
        api.updateEvent("access-1", calendar.id(), "g1", arzt);
        server.verify();
    }

    @Test
    void deletingWhatIsAlreadyGoneAtGoogleIsFine() {
        server.expect(requestTo(FAMILYHUB_URL + "/events/g1"))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(HttpStatus.GONE));
        server.expect(requestTo(FAMILYHUB_URL))
                .andExpect(method(HttpMethod.DELETE))
                .andRespond(withStatus(HttpStatus.NOT_FOUND));

        api.deleteEvent("access-1", FAMILYHUB, "g1");
        api.deleteCalendar("access-1", FAMILYHUB);
        server.verify();
    }

    @Test
    void missingCalendarIsReportedAsNotFound() {
        server.expect(requestTo(startsWith(FAMILYHUB_URL + "/events?")))
                .andRespond(withStatus(HttpStatus.NOT_FOUND).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"error\": {\"code\": 404, \"message\": \"Not Found\"}}"));

        assertThatThrownBy(() -> api.listEvents("access-1", FAMILYHUB,
                Instant.parse("2025-09-28T00:00:00Z"), Instant.parse("2027-09-28T00:00:00Z")))
                .isInstanceOfSatisfying(GoogleException.class, e -> {
                    assertThat(e.notFound()).isTrue();
                    assertThat(e.reconnectNeeded()).isFalse();
                });
    }
}
