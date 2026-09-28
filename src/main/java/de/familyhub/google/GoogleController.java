package de.familyhub.google;

import java.net.URI;
import java.security.SecureRandom;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.util.UriComponentsBuilder;

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
    private final GoogleConnectionRepository connections;
    private final CurrentMember currentMember;
    private final Permissions permissions;

    public GoogleController(GoogleProperties properties, GoogleApi google, GoogleAccountService accounts,
            GoogleConnectionRepository connections, CurrentMember currentMember, Permissions permissions) {
        this.properties = properties;
        this.google = google;
        this.accounts = accounts;
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
            accounts.connect(viewer.id(), code);
            return backToFrontend("verbunden");
        } catch (GoogleException e) {
            LOG.warn("Google-Verbindung für Mitglied {} fehlgeschlagen: {}", viewer.id(), e.getMessage());
            return backToFrontend("fehler");
        }
    }

    @DeleteMapping
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Google-Verbindung trennen",
            description = "Widerruft den Zugang bei Google und löscht die Verbindung.")
    public void disconnect() {
        FamilyMember viewer = requireConnectRight();
        GoogleConnection connection = connections.findByMemberId(viewer.id())
                .orElseThrow(() -> ApiException.notFound("Es ist kein Google-Kalender verbunden."));
        accounts.disconnect(connection);
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
