package de.familyhub.notifications;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.familyhub.calendar.CalendarEvent;
import de.familyhub.calendar.EventCategory;

// Bausteine der Benachrichtigungstexte
class NotificationTextsTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 10, 21);

    @Test
    void daysNearTodayGetWords() {
        assertThat(NotificationTexts.day(TODAY, TODAY)).isEqualTo("heute");
        assertThat(NotificationTexts.day(TODAY.plusDays(1), TODAY)).isEqualTo("morgen");
        assertThat(NotificationTexts.day(TODAY.plusDays(3), TODAY)).isEqualTo("am 24.10.");
        assertThat(NotificationTexts.dayAndTime(LocalDateTime.of(2026, 10, 22, 9, 5), TODAY))
                .isEqualTo("morgen um 09:05");
    }

    @Test
    void durationsReadAfterSeitUeber() {
        assertThat(NotificationTexts.duration(Duration.ofHours(24))).isEqualTo("24 Stunden");
        assertThat(NotificationTexts.duration(Duration.ofHours(1))).isEqualTo("einer Stunde");
        assertThat(NotificationTexts.duration(Duration.ofMinutes(30))).isEqualTo("30 Minuten");
    }

    @Test
    void oneLineStaysOnTheHeadingSeveralBecomeAList() {
        assertThat(NotificationTexts.list("⏰ Heute fällig:", List.of("„A“"))).isEqualTo("⏰ Heute fällig: „A“");
        assertThat(NotificationTexts.list("⏰ Heute fällig:", List.of("„A“", "„B“")))
                .isEqualTo("⏰ Heute fällig:\n• „A“\n• „B“");
    }

    @Test
    void allDayEventsHaveNoTime() {
        CalendarEvent einTag = new CalendarEvent(null, "Wandertag", LocalDateTime.of(2026, 10, 22, 0, 0),
                LocalDateTime.of(2026, 10, 23, 0, 0), "m", EventCategory.FAMILY, null, null);
        CalendarEvent ferien = new CalendarEvent(null, "Herbstferien", LocalDateTime.of(2026, 10, 26, 0, 0),
                LocalDateTime.of(2026, 10, 31, 0, 0), "m", EventCategory.FAMILY, null, null);
        CalendarEvent arzt = new CalendarEvent(null, "Arzt", LocalDateTime.of(2026, 10, 22, 9, 5),
                LocalDateTime.of(2026, 10, 22, 10, 0), "m", EventCategory.APPOINTMENT, null, null);

        assertThat(NotificationTexts.when(einTag, TODAY)).isEqualTo("morgen (ganztägig)");
        assertThat(NotificationTexts.when(ferien, TODAY)).isEqualTo("am 26.10. bis 30.10. (ganztägig)");
        assertThat(NotificationTexts.when(arzt, TODAY)).isEqualTo("morgen um 09:05");
    }
}
