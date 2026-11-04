package de.familyhub.task;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

class TaskRepeatTest {

    private static final LocalDate DONE = LocalDate.of(2027, 1, 31);

    @Test
    void nextRoundIsDueTheIntervalAfterTheDayItWasDone() {
        assertThat(new TaskRepeat(3, RepeatUnit.DAYS).nextDue(DONE)).isEqualTo(LocalDate.of(2027, 2, 3));
        assertThat(new TaskRepeat(2, RepeatUnit.WEEKS).nextDue(DONE)).isEqualTo(LocalDate.of(2027, 2, 14));
        assertThat(new TaskRepeat(3, RepeatUnit.MONTHS).nextDue(DONE)).isEqualTo(LocalDate.of(2027, 4, 30));
    }

    // Anders als bei Serienterminen wird nichts übersprungen: Fehlt der Tag, ist der letzte des Monats fällig.
    @Test
    void monthsEndingEarlierFallOnTheirLastDay() {
        assertThat(new TaskRepeat(1, RepeatUnit.MONTHS).nextDue(DONE)).isEqualTo(LocalDate.of(2027, 2, 28));
    }
}
