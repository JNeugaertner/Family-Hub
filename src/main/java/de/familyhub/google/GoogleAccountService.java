package de.familyhub.google;

import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import de.familyhub.calendar.EventCategory;

// Verbinden und Trennen eines Google-Kontos.
@Service
public class GoogleAccountService {

    private static final Logger LOG = LoggerFactory.getLogger(GoogleAccountService.class);

    // Neue Kalender landen in dieser Kategorie, bis die Person eine andere wählt.
    static final EventCategory DEFAULT_CATEGORY = EventCategory.APPOINTMENT;

    private final GoogleApi google;
    private final GoogleConnectionRepository connections;
    private final TokenCipher cipher;
    private final Clock clock;

    public GoogleAccountService(GoogleApi google, GoogleConnectionRepository connections, TokenCipher cipher,
            Clock clock) {
        this.google = google;
        this.connections = connections;
        this.cipher = cipher;
        this.clock = clock;
    }

    // Tauscht den Code aus dem Google-Callback gegen Tokens und speichert die Verbindung samt Kalenderliste.
    // Beim erneuten Verbinden bleibt die bisherige Kalenderauswahl erhalten.
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

        List<GoogleCalendarChoice> choices = calendars.stream()
                .map(c -> previous.containsKey(c.id())
                        ? previous.get(c.id()).withName(c.name(), c.isPrimary())
                        : new GoogleCalendarChoice(c.id(), c.name(), c.isPrimary(), c.isPrimary(), DEFAULT_CATEGORY))
                .toList();
        String email = calendars.stream().filter(GoogleApi.CalendarInfo::isPrimary)
                .map(GoogleApi.CalendarInfo::id).findFirst().orElse(null);

        return connections.save(new GoogleConnection(existing == null ? null : existing.id(), memberId, email,
                cipher.encrypt(tokens.refreshToken()), clock.instant(), choices, null, null, false));
    }

    // Widerruft den Zugang bei Google (Fehler dort verhindern das Trennen nicht) und löscht die Verbindung.
    public void disconnect(GoogleConnection connection) {
        try {
            google.revoke(cipher.decrypt(connection.encryptedRefreshToken()));
        } catch (RuntimeException e) {
            LOG.warn("Google-Zugang von Mitglied {} konnte nicht widerrufen werden: {}", connection.memberId(),
                    e.getMessage());
        }
        connections.deleteById(connection.id());
    }
}
