package de.familyhub.google;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.springframework.stereotype.Component;

import de.familyhub.calendar.CalendarEvent;
import de.familyhub.calendar.EventStatus;
import de.familyhub.calendar.ExternalRef;

// Wandelt Termine zwischen Google und FamilyHub um: Google-Termine werden FamilyHub-Termine der verbundenen Person,
// FamilyHub-Termine werden Einträge im Google-Kalender „FamilyHub“.
@Component
public class GoogleEventMapper {

    static final String NO_TITLE = "(Ohne Titel)";

    // Google erlaubt Termine ohne Dauer, FamilyHub verlangt Ende nach Beginn.
    static final Duration MIN_DURATION = Duration.ofMinutes(30);

    private final ZoneId zone;

    public GoogleEventMapper(Clock clock) {
        this.zone = clock.getZone();
    }

    // null für abgesagte oder unvollständige Termine; die werden nicht übernommen. Ebenso für Termine, die FamilyHub
    // selbst nach Google eingetragen hat (z. B. wenn jemand den Kalender „FamilyHub“ eines anderen abonniert hat):
    // Die gibt es in FamilyHub schon.
    public CalendarEvent toEvent(GoogleEvent event, String memberId, GoogleCalendarChoice calendar) {
        if ("cancelled".equals(event.status()) || event.start() == null || event.end() == null
                || event.familyhubId() != null) {
            return null;
        }
        LocalDateTime start = toLocal(event.start());
        LocalDateTime end = toLocal(event.end());
        if (start == null || end == null) {
            return null;
        }
        if (!end.isAfter(start)) {
            end = start.plus(MIN_DURATION);
        }
        String title = cut(event.summary(), 100);
        boolean privateEvent = "private".equals(event.visibility()) || "confidential".equals(event.visibility());
        return new CalendarEvent(null, title == null ? NO_TITLE : title, start, end, List.of(memberId), calendar.category(),
                cut(event.location(), 200), cut(withoutHtml(event.description()), 1000), privateEvent,
                EventStatus.APPROVED, memberId, new ExternalRef(ExternalRef.GOOGLE, calendar.calendarId(), event.id()));
    }

    // Eintrag für den Google-Kalender „FamilyHub“. Private Termine sind auch in Google privat; die Id des
    // FamilyHub-Termins steht als private Eigenschaft daran, so erkennt der Abgleich ihn wieder.
    public GoogleEventInput toGoogle(CalendarEvent event) {
        return new GoogleEventInput(event.title(), blankToNull(event.description()), blankToNull(event.location()),
                event.privateEvent() ? "private" : "default", toGoogleTime(event.start()), toGoogleTime(event.end()),
                new GoogleEventInput.ExtendedProperties(Map.of(GoogleEvent.FAMILYHUB_ID, event.id())));
    }

    // Stimmt der Eintrag in Google noch mit dem FamilyHub-Termin überein? Sonst wird er überschrieben, auch wenn ihn
    // jemand in Google geändert hat: FamilyHub bleibt führend (Entscheidung vom 19.10.2026).
    public boolean isUpToDate(GoogleEvent google, CalendarEvent event) {
        if (google.start() == null || google.end() == null
                || google.start().dateTime() == null || google.end().dateTime() == null) {
            return false;
        }
        return Objects.equals(google.summary(), event.title())
                && Objects.equals(blankToNull(google.description()), blankToNull(event.description()))
                && Objects.equals(blankToNull(google.location()), blankToNull(event.location()))
                && "private".equals(google.visibility()) == event.privateEvent()
                && toLocal(google.start()).equals(event.start())
                && toLocal(google.end()).equals(event.end());
    }

    // Zeiten mit Zeitzone in die Zeitzone von FamilyHub umrechnen; ganztägige Termine gehen von 00:00 bis 00:00
    // des Folgetags (Google liefert das Ende bereits exklusiv).
    private LocalDateTime toLocal(GoogleEvent.Time time) {
        if (time.dateTime() != null) {
            return time.dateTime().atZoneSameInstant(zone).toLocalDateTime();
        }
        return time.date() == null ? null : time.date().atStartOfDay();
    }

    private GoogleEventInput.Time toGoogleTime(LocalDateTime time) {
        return new GoogleEventInput.Time(time.atZone(zone).format(DateTimeFormatter.ISO_OFFSET_DATE_TIME), zone.getId());
    }

    // Google liefert Beschreibungen teils als HTML
    private static String withoutHtml(String text) {
        return text == null ? null : text.replaceAll("(?i)<br\\s*/?>", "\n").replaceAll("<[^>]+>", "");
    }

    private static String cut(String text, int max) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String trimmed = text.strip();
        return trimmed.length() <= max ? trimmed : trimmed.substring(0, max - 1) + "…";
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text;
    }
}
