package de.familyhub.google;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import org.springframework.stereotype.Component;

import de.familyhub.calendar.CalendarEvent;
import de.familyhub.calendar.EventStatus;
import de.familyhub.calendar.ExternalRef;

// Wandelt einen Google-Termin in einen FamilyHub-Termin der verbundenen Person um.
@Component
public class GoogleEventMapper {

    static final String NO_TITLE = "(Ohne Titel)";

    // Google erlaubt Termine ohne Dauer, FamilyHub verlangt Ende nach Beginn.
    static final Duration MIN_DURATION = Duration.ofMinutes(30);

    private final ZoneId zone;

    public GoogleEventMapper(Clock clock) {
        this.zone = clock.getZone();
    }

    // null für abgesagte oder unvollständige Termine; die werden nicht übernommen.
    public CalendarEvent toEvent(GoogleEvent event, String memberId, GoogleCalendarChoice calendar) {
        if ("cancelled".equals(event.status()) || event.start() == null || event.end() == null) {
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

    // Zeiten mit Zeitzone in die Zeitzone von FamilyHub umrechnen; ganztägige Termine gehen von 00:00 bis 00:00
    // des Folgetags (Google liefert das Ende bereits exklusiv).
    private LocalDateTime toLocal(GoogleEvent.Time time) {
        if (time.dateTime() != null) {
            return time.dateTime().atZoneSameInstant(zone).toLocalDateTime();
        }
        return time.date() == null ? null : time.date().atStartOfDay();
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
}
