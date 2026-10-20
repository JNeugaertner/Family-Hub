package de.familyhub.google;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import de.familyhub.calendar.CalendarEventRepository;
import de.familyhub.calendar.EventCategory;

// Verbinden und Trennen eines Google-Kontos.
@Service
public class GoogleAccountService {

    private static final Logger LOG = LoggerFactory.getLogger(GoogleAccountService.class);

    // Neue Kalender landen in dieser Kategorie, bis die Person eine andere wählt.
    static final EventCategory DEFAULT_CATEGORY = EventCategory.APPOINTMENT;

    private final GoogleApi google;
    private final GoogleConnectionRepository connections;
    private final CalendarEventRepository events;
    private final TokenCipher cipher;
    private final Clock clock;

    public GoogleAccountService(GoogleApi google, GoogleConnectionRepository connections,
            CalendarEventRepository events, TokenCipher cipher, Clock clock) {
        this.google = google;
        this.connections = connections;
        this.events = events;
        this.cipher = cipher;
        this.clock = clock;
    }

    // Tauscht den Code aus dem Google-Callback gegen Tokens und speichert die Verbindung samt Kalenderliste.
    // Beim erneuten Verbinden bleiben die bisherige Kalenderauswahl und das Eintragen erhalten. Der Kalender
    // „FamilyHub“ steht nicht zur Auswahl: Seine Termine kommen aus FamilyHub.
    public GoogleConnection connect(String memberId, String code) {
        GoogleApi.Tokens tokens = google.exchangeCode(code);
        if (tokens.refreshToken() == null) {
            throw new GoogleException("Google hat keinen dauerhaften Zugang geliefert.", false);
        }
        List<GoogleApi.CalendarInfo> calendars = google.listCalendars(tokens.accessToken());
        GoogleConnection existing = connections.findByMemberId(memberId).orElse(null);
        Map<String, GoogleCalendarChoice> previous = existing == null ? Map.of()
                : existing.calendars().stream()
                        .collect(Collectors.toMap(GoogleCalendarChoice::calendarId, Function.identity()));
        String exportCalendarId = existing == null ? null : existing.exportCalendarId();
        boolean writeGranted = tokens.grants(GoogleApi.WRITE_SCOPE);

        List<GoogleCalendarChoice> choices = calendars.stream()
                .filter(c -> !c.id().equals(exportCalendarId))
                .map(c -> previous.containsKey(c.id())
                        ? previous.get(c.id()).withName(c.name(), c.isPrimary())
                        : new GoogleCalendarChoice(c.id(), c.name(), c.isPrimary(), c.isPrimary(), DEFAULT_CATEGORY))
                .toList();
        String email = calendars.stream().filter(GoogleApi.CalendarInfo::isPrimary)
                .map(GoogleApi.CalendarInfo::id).findFirst().orElse(null);

        // Ohne Schreibrecht kann FamilyHub nicht mehr eintragen; der Kalender „FamilyHub“ bleibt dann in Google stehen.
        return connections.save(new GoogleConnection(existing == null ? null : existing.id(), memberId, email,
                cipher.encrypt(tokens.refreshToken()), clock.instant(), choices, null, null, false, writeGranted,
                writeGranted ? exportCalendarId : null));
    }

    // Löscht den Kalender „FamilyHub“ in Google und widerruft den Zugang (Fehler dort verhindern das Trennen nicht),
    // dann die Verbindung und alle importierten Termine der Person (Entscheidung vom 28.09.2026).
    public void disconnect(GoogleConnection connection) {
        if (connection.exportEnabled()) {
            try {
                String accessToken = google.refreshAccessToken(cipher.decrypt(connection.encryptedRefreshToken()));
                google.deleteCalendar(accessToken, connection.exportCalendarId());
            } catch (RuntimeException e) {
                LOG.warn("Kalender „FamilyHub“ von Mitglied {} konnte in Google nicht gelöscht werden: {}",
                        connection.memberId(), e.getMessage());
            }
        }
        try {
            google.revoke(cipher.decrypt(connection.encryptedRefreshToken()));
        } catch (RuntimeException e) {
            LOG.warn("Google-Zugang von Mitglied {} konnte nicht widerrufen werden: {}", connection.memberId(),
                    e.getMessage());
        }
        connections.deleteById(connection.id());
        events.deleteImportedByMember(connection.memberId());
    }

    // Beim Löschen eines Familienmitglieds: Verbindung trennen und importierte Termine entfernen.
    public void forgetMember(String memberId) {
        connections.findByMemberId(memberId).ifPresent(this::disconnect);
        events.deleteImportedByMember(memberId);
    }
}
