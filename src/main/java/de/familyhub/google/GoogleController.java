package de.familyhub.google;

import java.net.URI;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

import de.familyhub.calendar.EventCategory;
import de.familyhub.family.FamilyMember;
import de.familyhub.permission.Action;
import de.familyhub.permission.Module;
import de.familyhub.permission.Permissions;
import de.familyhub.permission.Role;
import de.familyhub.permission.Scope;
import de.familyhub.security.CurrentMember;
import de.familyhub.web.ApiException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpSession;

// Google Kalender verbinden (nur lesen). Ablauf: POST /connect liefert die Anmeldeseite von Google, Google leitet
// danach auf GET /callback zurück, von dort geht es weiter ins Frontend (?google=verbunden|abgebrochen|fehler).
// Verbinden darf jede Person außer Gästen, die ihre eigenen Termine sehen darf (Entscheidung vom 28.09.2026).
@RestController
@RequestMapping("/api/google")
@Tag(name = "Google Kalender")
public class GoogleController {

    private static final Logger LOG = LoggerFactory.getLogger(GoogleController.class);

    static final String STATE_ATTRIBUTE = "familyhub.google.state";

    private static final SecureRandom RANDOM = new SecureRandom();

    private final GoogleProperties properties;
    private final GoogleApi google;
    private final GoogleAccountService accounts;
    private final GoogleCalendarSync sync;
    private final GoogleConnectionRepository connections;
    private final CurrentMember currentMember;
    private final Permissions permissions;

    public GoogleController(GoogleProperties properties, GoogleApi google, GoogleAccountService accounts,
            GoogleCalendarSync sync, GoogleConnectionRepository connections, CurrentMember currentMember,
            Permissions permissions) {
        this.properties = properties;
        this.google = google;
        this.accounts = accounts;
        this.sync = sync;
        this.connections = connections;
        this.currentMember = currentMember;
        this.permissions = permissions;
    }

    public record GoogleStatus(boolean configured, boolean connected, String email,
            List<GoogleCalendarChoice> calendars, Instant lastSyncAt, String lastSyncError, boolean needsReconnect) {

        static GoogleStatus of(boolean configured, GoogleConnection connection) {
            if (connection == null) {
                return new GoogleStatus(configured, false, null, List.of(), null, null, false);
            }
            return new GoogleStatus(configured, true, connection.googleEmail(), connection.calendars(),
                    connection.lastSyncAt(), connection.lastSyncError(), connection.needsReconnect());
        }
    }

    public record ConnectResponse(String authorizationUrl) {
    }

    @GetMapping("/status")
    @Operation(summary = "Status der eigenen Google-Verbindung",
            description = "configured = false: Die Anbindung ist auf dem Server nicht eingerichtet (fehlende Zugangsdaten).")
    public GoogleStatus status() {
        FamilyMember viewer = requireConnectRight();
        return GoogleStatus.of(properties.isConfigured(), connections.findByMemberId(viewer.id()).orElse(null));
    }

    @PostMapping("/connect")
    @Operation(summary = "Google Kalender verbinden",
            description = "Liefert die Adresse der Google-Anmeldeseite. Das Frontend leitet den Browser dorthin weiter.")
    public ConnectResponse connect(HttpSession session) {
        requireConnectRight();
        requireConfigured();
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String state = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        session.setAttribute(STATE_ATTRIBUTE, state);
        return new ConnectResponse(google.authorizationUrl(state));
    }

    @GetMapping("/callback")
    @Operation(summary = "Rückkehr von Google (nur für Google)",
            description = "Google ruft diese Adresse nach der Anmeldung auf. Leitet ins Frontend weiter.")
    public ResponseEntity<Void> callback(@RequestParam(required = false) String code,
            @RequestParam(required = false) String state, @RequestParam(required = false) String error,
            HttpSession session) {
        FamilyMember viewer = requireConnectRight();
        Object expectedState = session.getAttribute(STATE_ATTRIBUTE);
        session.removeAttribute(STATE_ATTRIBUTE);

        // Ohne passenden state könnte ein fremder Link ein fremdes Google-Konto unterschieben (CSRF).
        if (expectedState == null || !expectedState.equals(state)) {
            return backToFrontend("fehler");
        }
        if (error != null || code == null) {
            return backToFrontend("abgebrochen");
        }
        try {
            // Fehler beim ersten Abgleich stehen im Status; verbunden ist das Konto trotzdem.
            sync.sync(accounts.connect(viewer.id(), code));
            return backToFrontend("verbunden");
        } catch (GoogleException e) {
            LOG.warn("Google-Verbindung für Mitglied {} fehlgeschlagen: {}", viewer.id(), e.getMessage());
            return backToFrontend("fehler");
        }
    }

    @PostMapping("/sync")
    @Operation(summary = "Jetzt mit Google abgleichen",
            description = "Übernimmt neue, geänderte und gelöschte Termine. Fehler stehen in lastSyncError.")
    public GoogleStatus syncNow() {
        FamilyMember viewer = requireConnectRight();
        requireConfigured();
        return GoogleStatus.of(true, sync.sync(requireConnection(viewer)));
    }

    @PutMapping("/calendars")
    @Operation(summary = "Kalenderauswahl speichern",
            description = "Welche Google-Kalender übernommen werden und in welcher Kategorie. Danach wird abgeglichen.")
    public GoogleStatus selectCalendars(@RequestBody List<CalendarSelection> selection) {
        FamilyMember viewer = requireConnectRight();
        requireConfigured();
        GoogleConnection connection = requireConnection(viewer);
        Set<String> known = connection.calendars().stream().map(GoogleCalendarChoice::calendarId)
                .collect(Collectors.toSet());
        for (CalendarSelection s : selection) {
            if (s.calendarId() == null || !known.contains(s.calendarId())) {
                throw ApiException.invalidField("calendarId", "Unbekannter Kalender: " + s.calendarId());
            }
            if (s.category() == null) {
                throw ApiException.invalidField("category", "Kategorie ist Pflicht");
            }
        }
        Map<String, CalendarSelection> byId = selection.stream()
                .collect(Collectors.toMap(CalendarSelection::calendarId, Function.identity(), (a, b) -> b));

        List<GoogleCalendarChoice> choices = connection.calendars().stream()
                .map(c -> byId.containsKey(c.calendarId())
                        ? new GoogleCalendarChoice(c.calendarId(), c.name(), c.primary(),
                                byId.get(c.calendarId()).enabled(), byId.get(c.calendarId()).category())
                        : c)
                .toList();
        return GoogleStatus.of(true, sync.sync(connections.save(connection.withCalendars(choices))));
    }

    public record CalendarSelection(String calendarId, boolean enabled, EventCategory category) {
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Google-Verbindung trennen",
            description = "Widerruft den Zugang bei Google, löscht die Verbindung und alle importierten Termine.")
    public void disconnect() {
        FamilyMember viewer = requireConnectRight();
        accounts.disconnect(requireConnection(viewer));
    }

    private GoogleConnection requireConnection(FamilyMember viewer) {
        return connections.findByMemberId(viewer.id())
                .orElseThrow(() -> ApiException.notFound("Es ist kein Google-Kalender verbunden."));
    }

    private FamilyMember requireConnectRight() {
        FamilyMember viewer = currentMember.get();
        String denied = "Keine Berechtigung, einen Google-Kalender zu verbinden.";
        if (viewer.role() == Role.GAST) {
            throw ApiException.forbidden(denied);
        }
        permissions.require(viewer, Module.KALENDER, Action.ANSEHEN, Scope.EIGEN, denied);
        return viewer;
    }

    private void requireConfigured() {
        if (!properties.isConfigured()) {
            throw ApiException.conflict("Die Google-Anbindung ist nicht eingerichtet.");
        }
    }

    private ResponseEntity<Void> backToFrontend(String result) {
        URI target = UriComponentsBuilder.fromUriString(properties.frontendUrl())
                .queryParam("google", result)
                .build()
                .toUri();
        return ResponseEntity.status(HttpStatus.FOUND).location(target).build();
    }
}
