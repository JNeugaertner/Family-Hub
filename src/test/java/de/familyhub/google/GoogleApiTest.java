package de.familyhub.google;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.content;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withBadRequest;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

// Die Aufrufe an Google gegen einen simulierten Server: Formulare, Header, Blättern und Fehler.
class GoogleApiTest {

    private static final GoogleProperties PROPERTIES = new GoogleProperties("client-id", "client-secret", "key",
            "http://localhost:8080/api/google/callback", "http://localhost:5173", Duration.ofMinutes(15),
            "https://auth.test/auth", "https://auth.test/token", "https://auth.test/revoke",
            "https://api.test/calendar/v3");

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
        String url = api.authorizationUrl("state-123");

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
        server.verify();
    }
}
