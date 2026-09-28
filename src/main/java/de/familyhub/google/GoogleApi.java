package de.familyhub.google;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

import com.fasterxml.jackson.annotation.JsonProperty;

// Aufrufe an Google: Anmeldung (OAuth 2.0, Autorisierungs-Code-Ablauf) und Google Calendar API (nur lesen).
@Component
public class GoogleApi {

    static final String SCOPE = "https://www.googleapis.com/auth/calendar.readonly";

    // Schutz gegen Endlosschleifen beim Blättern; 20 Seiten à 2500 Termine reichen für jeden Familienkalender.
    private static final int MAX_PAGES = 20;

    private final GoogleProperties properties;
    private final RestClient rest;

    @Autowired
    public GoogleApi(GoogleProperties properties) {
        this(properties, RestClient.builder());
    }

    // Für Tests, die Google mit MockRestServiceServer simulieren
    GoogleApi(GoogleProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.rest = builder.build();
    }

    public record Tokens(
            @JsonProperty("access_token") String accessToken,
            @JsonProperty("refresh_token") String refreshToken) {
    }

    public record CalendarInfo(String id, String summary, String summaryOverride, Boolean primary) {

        public String name() {
            return summaryOverride != null ? summaryOverride : summary;
        }

        public boolean isPrimary() {
            return Boolean.TRUE.equals(primary);
        }
    }

    record Page<T>(List<T> items, String nextPageToken) {
    }

    // access_type=offline und prompt=consent: Google liefert einen Refresh-Token, auch beim erneuten Verbinden.
    public String authorizationUrl(String state) {
        return UriComponentsBuilder.fromUriString(properties.authUrl())
                .queryParam("client_id", properties.clientId())
                .queryParam("redirect_uri", properties.redirectUri())
                .queryParam("response_type", "code")
                .queryParam("scope", SCOPE)
                .queryParam("access_type", "offline")
                .queryParam("prompt", "consent")
                .queryParam("state", state)
                .encode()
                .toUriString();
    }

    public Tokens exchangeCode(String code) {
        MultiValueMap<String, String> form = clientForm();
        form.add("grant_type", "authorization_code");
        form.add("code", code);
        form.add("redirect_uri", properties.redirectUri());
        return requestTokens(form);
    }

    public String refreshAccessToken(String refreshToken) {
        MultiValueMap<String, String> form = clientForm();
        form.add("grant_type", "refresh_token");
        form.add("refresh_token", refreshToken);
        return requestTokens(form).accessToken();
    }

    public void revoke(String token) {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("token", token);
        rest.post().uri(properties.revokeUrl())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> {
                    throw apiError(response);
                })
                .toBodilessEntity();
    }

    public List<CalendarInfo> listCalendars(String accessToken) {
        return allPages(pageToken -> rest.get()
                .uri(properties.apiUrl() + "/users/me/calendarList?maxResults=250" + pageParam(pageToken),
                        pageVars(pageToken))
                .headers(headers -> headers.setBearerAuth(accessToken))
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> {
                    throw apiError(response);
                })
                .body(new ParameterizedTypeReference<Page<CalendarInfo>>() {
                }));
    }

    // singleEvents=true: Serientermine kommen als einzelne Termine, nach Beginn sortiert.
    public List<GoogleEvent> listEvents(String accessToken, String calendarId, Instant timeMin, Instant timeMax) {
        return allPages(pageToken -> {
            Map<String, Object> vars = new HashMap<>(pageVars(pageToken));
            vars.put("calendarId", calendarId);
            vars.put("timeMin", timeMin.toString());
            vars.put("timeMax", timeMax.toString());
            return rest.get()
                    .uri(properties.apiUrl() + "/calendars/{calendarId}/events?singleEvents=true&orderBy=startTime"
                            + "&maxResults=2500&timeMin={timeMin}&timeMax={timeMax}"
                            + pageParam(pageToken), vars)
                    .headers(headers -> headers.setBearerAuth(accessToken))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        throw apiError(response);
                    })
                    .body(new ParameterizedTypeReference<Page<GoogleEvent>>() {
                    });
        });
    }

    private MultiValueMap<String, String> clientForm() {
        MultiValueMap<String, String> form = new LinkedMultiValueMap<>();
        form.add("client_id", properties.clientId());
        form.add("client_secret", properties.clientSecret());
        return form;
    }

    private Tokens requestTokens(MultiValueMap<String, String> form) {
        return rest.post().uri(properties.tokenUrl())
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .body(form)
                .retrieve()
                .onStatus(HttpStatusCode::isError, (request, response) -> {
                    String body = bodyOf(response);
                    if (body.contains("invalid_grant")) {
                        throw new GoogleException("Der Zugang zu Google ist abgelaufen oder wurde widerrufen. "
                                + "Bitte den Google Kalender neu verbinden.", true);
                    }
                    throw new GoogleException("Anmeldung bei Google fehlgeschlagen (HTTP "
                            + response.getStatusCode().value() + ").", false);
                })
                .body(Tokens.class);
    }

    // Häufige Ursachen verständlich melden; sonst Googles eigene Meldung anhängen, damit sie im Log steht.
    private static GoogleException apiError(ClientHttpResponse response) throws IOException {
        String body = bodyOf(response);
        if (body.contains("accessNotConfigured") || body.contains("SERVICE_DISABLED")) {
            return new GoogleException("Die Google Calendar API ist im Google-Cloud-Projekt nicht aktiviert.", false);
        }
        if (body.contains("insufficientPermissions") || body.contains("ACCESS_TOKEN_SCOPE_INSUFFICIENT")) {
            return new GoogleException("Der Zugriff auf den Kalender wurde bei Google nicht erlaubt. Bitte neu verbinden "
                    + "und den Kalenderzugriff zulassen.", true);
        }
        String detail = body.replaceAll("\\s+", " ").strip();
        return new GoogleException("Google hat die Anfrage abgelehnt (HTTP " + response.getStatusCode().value() + "): "
                + (detail.length() > 300 ? detail.substring(0, 300) + "…" : detail), false);
    }

    private static String bodyOf(ClientHttpResponse response) throws IOException {
        return new String(response.getBody().readAllBytes(), UTF_8);
    }

    // Seitentoken als URI-Variable, damit Sonderzeichen wie + sicher kodiert werden
    private static String pageParam(String pageToken) {
        return pageToken == null ? "" : "&pageToken={pageToken}";
    }

    private static Map<String, Object> pageVars(String pageToken) {
        return pageToken == null ? Map.of() : Map.of("pageToken", pageToken);
    }

    private static <T> List<T> allPages(Function<String, Page<T>> fetch) {
        List<T> all = new ArrayList<>();
        String pageToken = null;
        for (int i = 0; i < MAX_PAGES; i++) {
            Page<T> page = fetch.apply(pageToken);
            if (page == null) {
                break;
            }
            if (page.items() != null) {
                all.addAll(page.items());
            }
            pageToken = page.nextPageToken();
            if (pageToken == null) {
                break;
            }
        }
        return all;
    }
}
