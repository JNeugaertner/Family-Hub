package de.familyhub.calendar;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;

@DataMongoTest
class CalendarEventRepositoryTest {

    private static final LocalDateTime DAY_START = LocalDateTime.of(2026, 9, 25, 0, 0);
    private static final LocalDateTime DAY_END = DAY_START.plusDays(1);

    @Autowired
    private CalendarEventRepository repository;

    @BeforeEach
    void cleanDatabase() {
        repository.deleteAll();
    }

    private CalendarEvent save(String title, LocalDateTime start, LocalDateTime end, String memberId) {
        return repository.save(new CalendarEvent(null, title, start, end, memberId, EventCategory.FAMILY, null, null));
    }

    @Test
    void savedEventGetsIdAndKeepsAllFields() {
        CalendarEvent original = new CalendarEvent(null, "Zahnarzt", DAY_START.withHour(11), DAY_START.withHour(12),
                "m1", EventCategory.APPOINTMENT, "Bright Smile Dental", "Kontrolle");

        CalendarEvent saved = repository.save(original);

        assertThat(saved.id()).isNotBlank();
        assertThat(repository.findById(saved.id())).contains(saved);
    }

    @Test
    void findOverlappingReturnsEveryEventTouchingTheRangeSortedByStart() {
        save("Abends über Mitternacht", DAY_START.withHour(23), DAY_END.withHour(1), "m1");
        save("Morgens", DAY_START.withHour(8), DAY_START.withHour(9), "m1");
        save("Vom Vortag herüber", DAY_START.minusHours(1), DAY_START.plusHours(1), "m1");
        save("Endet genau zu Beginn", DAY_START.minusHours(2), DAY_START, "m1");
        save("Beginnt genau am Ende", DAY_END, DAY_END.plusHours(1), "m1");

        assertThat(repository.findOverlapping(DAY_START, DAY_END))
                .extracting(CalendarEvent::title)
                .containsExactly("Vom Vortag herüber", "Morgens", "Abends über Mitternacht");
    }

    @Test
    void findOverlappingForMemberIgnoresOtherMembers() {
        save("Training Emma", DAY_START.withHour(16), DAY_START.withHour(18), "emma");
        save("Klavier Lily", DAY_START.withHour(16), DAY_START.withHour(17), "lily");

        assertThat(repository.findOverlappingForMember("lily", DAY_START, DAY_END))
                .extracting(CalendarEvent::title)
                .containsExactly("Klavier Lily");
    }
}
