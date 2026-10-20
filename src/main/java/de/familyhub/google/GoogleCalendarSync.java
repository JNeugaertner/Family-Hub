package de.familyhub.google;

import java.time.Clock;
import java.time.Instant;
import java.time.Period;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClientException;

import de.familyhub.calendar.CalendarEvent;
import de.familyhub.calendar.CalendarEventRepository;
import de.familyhub.live.LiveUpdates;

// Gleicht eine Person mit Google ab, in beide Richtungen und jeweils über das ganze Zeitfenster:
// - Import: Termine der aktiven Google-Kalender übernehmen (neue anlegen, geänderte aktualisieren, in Google
//   gelöschte oder aus abgewählten Kalendern entfernen). Sie sind in FamilyHub schreibgeschützt.
// - Eintragen (optional): FamilyHub-Termine in den Google-Kalender „FamilyHub“ schreiben (GoogleCalendarExport).
// Schlägt ein Aufruf fehl, bleibt der bisherige Stand unverändert und der Fehler steht im Status.
// Alles läuft nacheinander (synchronized), damit z. B. zwei Abgleiche denselben Termin nicht doppelt eintragen.
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
    private final GoogleCalendarExport export;
    private final TokenCipher cipher;
    private final Clock clock;
    private final LiveUpdates live;

    // Seit dem letzten Eintragen haben sich FamilyHub-Termine geändert (CalendarEventChanges)
    private final AtomicBoolean pending = new AtomicBoolean();

    public GoogleCalendarSync(GoogleApi google, GoogleProperties properties, GoogleConnectionRepository connections,
            CalendarEventRepository events, GoogleEventMapper mapper, GoogleCalendarExport export, TokenCipher cipher,
            Clock clock, LiveUpdates live) {
        this.google = google;
        this.properties = properties;
        this.connections = connections;
        this.events = events;
        this.mapper = mapper;
        this.export = export;
        this.cipher = cipher;
        this.clock = clock;
        this.live = live;
    }

    public synchronized GoogleConnection sync(GoogleConnection connection) {
        return run(connection, true);
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

    void markPending() {
        pending.set(true);
    }

    // Trägt geänderte FamilyHub-Termine kurz nach der Änderung nach Google ein, ohne auf den nächsten Abgleich zu
    // warten. Mehrere Änderungen kurz hintereinander gehen in einem Durchgang.
    @Scheduled(fixedDelayString = "${familyhub.google.push-delay}",
            initialDelayString = "${familyhub.google.push-delay}")
    public synchronized void pushPending() {
        if (!properties.isConfigured() || !pending.getAndSet(false)) {
            return;
        }
        for (GoogleConnection connection : connections.findAll()) {
            if (!connection.exportEnabled() || connection.needsReconnect()) {
                continue;
            }
            try {
                run(connection, false);
            } catch (RuntimeException e) {
                LOG.warn("Eintragen nach Google für Mitglied {} fehlgeschlagen", connection.memberId(), e);
            }
        }
    }

    // Legt den Kalender „FamilyHub“ an, falls es ihn noch nicht gibt, und gleicht ab. Setzt das Schreibrecht voraus
    // (writeGranted). Fehler beim Anlegen gehen an den Aufrufer, damit die Person sie sofort sieht.
    public synchronized GoogleConnection enableExport(GoogleConnection given) {
        GoogleConnection connection = reload(given);
        if (!connection.exportEnabled()) {
            connection = export.createCalendar(connection, accessToken(connection));
        }
        return run(connection, true);
    }

    // Löscht den Kalender „FamilyHub“ samt Einträgen in Google. Ist der Zugang zu Google abgelaufen, kann FamilyHub
    // den Kalender nicht mehr löschen; ausgeschaltet wird trotzdem. Ist Google nicht erreichbar, bleibt es an.
    public synchronized GoogleConnection disableExport(GoogleConnection given) {
        GoogleConnection connection = reload(given);
        if (connection.exportEnabled()) {
            try {
                google.deleteCalendar(accessToken(connection), connection.exportCalendarId());
            } catch (GoogleException e) {
                if (!e.reconnectNeeded()) {
                    throw e;
                }
            }
        }
        return connections.save(connection.withExportCalendar(null));
    }

    // importToo = false: nur eintragen; der Status (zuletzt abgeglichen) ändert sich dann nur bei Fehlern.
    private GoogleConnection run(GoogleConnection given, boolean importToo) {
        GoogleConnection connection = reload(given);
        Instant now = clock.instant();
        ZonedDateTime today = now.atZone(clock.getZone());
        Instant from = today.minus(WINDOW).toInstant();
        Instant to = today.plus(WINDOW).toInstant();
        try {
            String accessToken = accessToken(connection);
            if (importToo) {
                apply(connection.memberId(), loadFromGoogle(connection, accessToken, from, to));
            }
            connection = export.export(connection, accessToken, from, to);
            return importToo ? saveIfConnected(connection.withSyncResult(now, null, false)) : connection;
        } catch (GoogleException e) {
            return failed(connection, e.getMessage(), e.reconnectNeeded());
        } catch (RestClientException e) {
            LOG.warn("Google-Abgleich für Mitglied {} fehlgeschlagen: {}", connection.memberId(), e.getMessage());
            return failed(connection, "Google ist gerade nicht erreichbar.", false);
        }
    }

    // Neu laden: Der Export kann den Kalender „FamilyHub“ schon neu angelegt haben, bevor der Fehler kam.
    private GoogleConnection failed(GoogleConnection connection, String error, boolean reconnect) {
        GoogleConnection current = reload(connection);
        return saveIfConnected(current.withSyncResult(current.lastSyncAt(), error, reconnect));
    }

    // Frisch aus der Datenbank: Seit dem Laden kann jemand die Kalenderauswahl oder das Eintragen geändert haben.
    private GoogleConnection reload(GoogleConnection connection) {
        return connection.id() == null ? connection : connections.findById(connection.id()).orElse(connection);
    }

    // Wer inzwischen getrennt hat, bekommt seine Verbindung nicht durch einen laufenden Abgleich zurück.
    private GoogleConnection saveIfConnected(GoogleConnection connection) {
        return connection.id() != null && !connections.existsById(connection.id()) ? connection
                : connections.save(connection);
    }

    private String accessToken(GoogleConnection connection) {
        return google.refreshAccessToken(cipher.decrypt(connection.encryptedRefreshToken()));
    }

    private Map<String, CalendarEvent> loadFromGoogle(GoogleConnection connection, String accessToken, Instant from,
            Instant to) {
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
        // Der automatische Abgleich kommt nicht über die API: geänderte Termine selbst melden
        if (!changed.isEmpty() || !removed.isEmpty()) {
            live.publish("google");
        }
    }

    private static CalendarEvent withId(CalendarEvent e, String id) {
        return new CalendarEvent(id, e.title(), e.start(), e.end(), e.memberIds(), e.category(), e.location(),
                e.description(), e.privateEvent(), e.status(), e.createdBy(), e.external());
    }
}
