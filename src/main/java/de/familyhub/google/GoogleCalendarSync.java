package de.familyhub.google;

import java.time.Clock;
import java.time.Instant;
import java.time.Period;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import de.familyhub.calendar.CalendarEvent;
import de.familyhub.calendar.CalendarEventRepository;

// Übernimmt die Termine der aktiven Google-Kalender einer Person (nur lesen). Abgeglichen wird das ganze Zeitfenster:
// neue Termine anlegen, geänderte aktualisieren, in Google gelöschte (oder aus abgewählten Kalendern) entfernen.
// Schlägt ein Aufruf fehl, bleibt der bisherige Stand unverändert und der Fehler steht im Status.
@Service
public class GoogleCalendarSync {

    private static final Logger LOG = LoggerFactory.getLogger(GoogleCalendarSync.class);

    // Entscheidung vom 28.09.2026: ein Jahr zurück und ein Jahr voraus
    static final Period WINDOW = Period.ofYears(1);

    private final GoogleApi google;
    private final GoogleProperties properties;
    private final GoogleConnectionRepository connections;
    private final CalendarEventRepository events;
    private final GoogleEventMapper mapper;
    private final TokenCipher cipher;
    private final Clock clock;

    public GoogleCalendarSync(GoogleApi google, GoogleProperties properties, GoogleConnectionRepository connections,
            CalendarEventRepository events, GoogleEventMapper mapper, TokenCipher cipher, Clock clock) {
        this.google = google;
        this.properties = properties;
        this.connections = connections;
        this.events = events;
        this.mapper = mapper;
        this.cipher = cipher;
        this.clock = clock;
    }

    public GoogleConnection sync(GoogleConnection connection) {
        Instant now = clock.instant();
        try {
            String accessToken = google.refreshAccessToken(cipher.decrypt(connection.encryptedRefreshToken()));
            Map<String, CalendarEvent> wanted = loadFromGoogle(connection, accessToken, now);
            apply(connection.memberId(), wanted);
            return connections.save(connection.withSyncResult(now, null, false));
        } catch (GoogleException e) {
            return connections.save(connection.withSyncResult(connection.lastSyncAt(), e.getMessage(),
                    e.reconnectNeeded()));
        } catch (RestClientException e) {
            LOG.warn("Google-Abgleich für Mitglied {} fehlgeschlagen: {}", connection.memberId(), e.getMessage());
            return connections.save(connection.withSyncResult(connection.lastSyncAt(),
                    "Google ist gerade nicht erreichbar.", false));
        }
    }

    // Automatischer Abgleich; die erste Runde erst nach einem Intervall, damit der Start schnell bleibt.
    @Scheduled(fixedDelayString = "${familyhub.google.sync-interval}",
            initialDelayString = "${familyhub.google.sync-interval}")
    public void syncAll() {
        if (!properties.isConfigured()) {
            return;
        }
        for (GoogleConnection connection : connections.findAll()) {
            if (connection.needsReconnect()) {
                continue;
            }
            try {
                sync(connection);
            } catch (RuntimeException e) {
                LOG.warn("Google-Abgleich für Mitglied {} fehlgeschlagen", connection.memberId(), e);
            }
        }
    }

    private Map<String, CalendarEvent> loadFromGoogle(GoogleConnection connection, String accessToken, Instant now) {
        ZonedDateTime today = now.atZone(clock.getZone());
        Instant from = today.minus(WINDOW).toInstant();
        Instant to = today.plus(WINDOW).toInstant();

        Map<String, CalendarEvent> wanted = new LinkedHashMap<>();
        for (GoogleCalendarChoice calendar : connection.calendars()) {
            if (!calendar.enabled()) {
                continue;
            }
            for (GoogleEvent googleEvent : google.listEvents(accessToken, calendar.calendarId(), from, to)) {
                CalendarEvent event = mapper.toEvent(googleEvent, connection.memberId(), calendar);
                if (event != null) {
                    wanted.put(event.external().key(), event);
                }
            }
        }
        return wanted;
    }

    private void apply(String memberId, Map<String, CalendarEvent> wanted) {
        Map<String, CalendarEvent> existing = events.findImportedByMember(memberId).stream()
                .collect(Collectors.toMap(e -> e.external().key(), Function.identity(), (a, b) -> a));

        List<CalendarEvent> changed = new ArrayList<>();
        wanted.forEach((key, event) -> {
            CalendarEvent old = existing.get(key);
            CalendarEvent updated = old == null ? event : withId(event, old.id());
            if (!updated.equals(old)) {
                changed.add(updated);
            }
        });
        events.saveAll(changed);

        List<CalendarEvent> removed = existing.entrySet().stream()
                .filter(entry -> !wanted.containsKey(entry.getKey()))
                .map(Map.Entry::getValue)
                .toList();
        events.deleteAll(removed);
    }

    private static CalendarEvent withId(CalendarEvent e, String id) {
        return new CalendarEvent(id, e.title(), e.start(), e.end(), e.memberIds(), e.category(), e.location(),
                e.description(), e.privateEvent(), e.status(), e.createdBy(), e.external());
    }
}
