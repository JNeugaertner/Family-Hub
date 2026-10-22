package de.familyhub.assistant.insights;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.familyhub.calendar.CalendarEvent;
import de.familyhub.calendar.EventCategory;
import de.familyhub.calendar.EventStatus;

// Welche Termine sich überschneiden (Entscheidung vom 22.10.2026)
class ConflictFinderTest {

    private static final LocalDateTime DAY = LocalDateTime.of(2026, 10, 24, 0, 0);

    private final ConflictFinder finder = new ConflictFinder();

    private static CalendarEvent event(String id, int fromHour, int toHour, EventStatus status, String... members) {
        return new CalendarEvent(id, id, DAY.withHour(fromHour), DAY.withHour(toHour), List.of(members),
                EventCategory.FAMILY, null, null, false, status, "creator", null);
    }

    private static CalendarEvent event(String id, int fromHour, int toHour, String... members) {
        return event(id, fromHour, toHour, EventStatus.APPROVED, members);
    }

    @Test
    void overlappingEventsOfTheSamePersonConflict() {
        List<EventConflict> conflicts = finder.find(List.of(
                event("Fussball", 16, 18, "emma"),
                event("Zahnarzt", 17, 18, "emma"),
                event("Klavier", 16, 17, "lucas")));

        assertThat(conflicts).singleElement().satisfies(c -> {
            assertThat(c.memberIds()).containsExactly("emma");
            assertThat(c.first().id()).isEqualTo("Fussball");
            assertThat(c.second().id()).isEqualTo("Zahnarzt");
            assertThat(c.involvesProposal()).isFalse();
        });
    }

    @Test
    void backToBackEventsDoNotConflict() {
        assertThat(finder.find(List.of(event("Schule", 8, 13, "emma"), event("Mittag", 13, 14, "emma")))).isEmpty();
    }

    @Test
    void theSamePairForSeveralPeopleIsOneConflict() {
        List<EventConflict> conflicts = finder.find(List.of(
                event("Familienessen", 18, 20, "sarah", "emma", "lucas"),
                event("Elternabend", 19, 21, "sarah", "emma")));

        assertThat(conflicts).singleElement()
                .satisfies(c -> assertThat(c.memberIds()).containsExactly("sarah", "emma"));
    }

    @Test
    void everyOverlappingPairCounts() {
        assertThat(finder.find(List.of(event("A", 9, 12, "emma"), event("B", 10, 11, "emma"),
                event("C", 11, 13, "emma"))))
                .extracting(c -> c.first().id() + "+" + c.second().id())
                .containsExactly("A+B", "A+C");
    }

    @Test
    void allDayEventsAreIgnored() {
        CalendarEvent vacation = new CalendarEvent("Urlaub", "Urlaub", DAY, DAY.plusDays(3), List.of("emma"),
                EventCategory.FAMILY, null, null, false, EventStatus.APPROVED, null, null);

        assertThat(finder.find(List.of(vacation, event("Zahnarzt", 10, 11, "emma")))).isEmpty();
    }

    @Test
    void proposalsAreCheckedAgainstEventsButNotAgainstEachOther() {
        List<EventConflict> conflicts = finder.find(List.of(
                event("Kino", 18, 20, EventStatus.PROPOSED, "emma", "lucas"),
                event("Party", 19, 22, EventStatus.PROPOSED, "emma"),
                event("Training", 17, 19, "lucas")));

        assertThat(conflicts).singleElement().satisfies(c -> {
            assertThat(c.memberIds()).containsExactly("lucas");
            assertThat(c.involvesProposal()).isTrue();
            assertThat(c.proposal().id()).isEqualTo("Kino");
        });
    }
}
