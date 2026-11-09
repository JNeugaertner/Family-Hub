package de.familyhub.calendar;

import static java.time.DayOfWeek.FRIDAY;
import static java.time.DayOfWeek.THURSDAY;
import static java.time.DayOfWeek.TUESDAY;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Set;

import org.junit.jupiter.api.Test;

// Regeln für Terminserien, ohne Datenbank. Der 03.11.2026 ist ein Dienstag.
class RecurrenceTest {

    private static final LocalDate TUESDAY_3RD = LocalDate.of(2026, 11, 3);
    private static final LocalDate END_OF_YEAR = LocalDate.of(2026, 12, 31);

    private static LocalDate day(int month, int dayOfMonth) {
        return LocalDate.of(2026, month, dayOfMonth);
    }

    @Test
    void weeklyWithoutWeekdaysRepeatsOnTheWeekdayOfTheStart() {
        Recurrence weekly = new Recurrence(Frequency.WEEKLY, null, null, null, null);

        assertThat(weekly.dates(TUESDAY_3RD, TUESDAY_3RD, day(11, 24)))
                .containsExactly(day(11, 3), day(11, 10), day(11, 17), day(11, 24));
        assertThat(weekly.interval()).isEqualTo(1);
    }

    // Beginnt die Serie an einem Montag, aber nur dienstags und donnerstags, ist der Montag kein Termin
    @Test
    void weeklyOnSeveralWeekdaysStartsAtTheFirstMatchingDay() {
        Recurrence tuesdayThursday = new Recurrence(Frequency.WEEKLY, 1, Set.of(THURSDAY, TUESDAY), null, null);

        assertThat(tuesdayThursday.dates(day(11, 2), day(11, 2), day(11, 12)))
                .containsExactly(day(11, 3), day(11, 5), day(11, 10), day(11, 12));
    }

    @Test
    void everyOtherWeekSkipsTheWeeksInBetween() {
        Recurrence everyOtherWeek = new Recurrence(Frequency.WEEKLY, 2, Set.of(TUESDAY, THURSDAY), null, 4);

        assertThat(everyOtherWeek.dates(TUESDAY_3RD, TUESDAY_3RD, END_OF_YEAR))
                .containsExactly(day(11, 3), day(11, 5), day(11, 17), day(11, 19));
    }

    @Test
    void dailyWithIntervalAndCount() {
        Recurrence everyThirdDay = new Recurrence(Frequency.DAILY, 3, null, null, 4);

        assertThat(everyThirdDay.dates(TUESDAY_3RD, TUESDAY_3RD, END_OF_YEAR))
                .containsExactly(day(11, 3), day(11, 6), day(11, 9), day(11, 12));
    }

    // Die Anzahl zählt ab dem ersten Termin, auch wenn nur ein späterer Zeitraum gefragt ist
    @Test
    void countStartsAtTheFirstTermEvenForALaterRange() {
        Recurrence fiveDays = new Recurrence(Frequency.DAILY, 1, null, null, 5);

        assertThat(fiveDays.dates(day(11, 1), day(11, 4), END_OF_YEAR)).containsExactly(day(11, 4), day(11, 5));
    }

    @Test
    void untilIsTheLastPossibleDay() {
        Recurrence untilThe17th = new Recurrence(Frequency.WEEKLY, 1, null, day(11, 17), null);

        assertThat(untilThe17th.dates(TUESDAY_3RD, TUESDAY_3RD, END_OF_YEAR))
                .containsExactly(day(11, 3), day(11, 10), day(11, 17));
    }

    // Wie bei Google: Monate ohne 31. fallen aus
    @Test
    void monthlyOnThe31stSkipsShorterMonths() {
        Recurrence monthly = new Recurrence(Frequency.MONTHLY, 1, null, null, null);
        LocalDate first = LocalDate.of(2027, 1, 31);

        assertThat(monthly.dates(first, first, LocalDate.of(2027, 6, 30)))
                .containsExactly(first, LocalDate.of(2027, 3, 31), LocalDate.of(2027, 5, 31));
    }

    @Test
    void yearlyOnLeapDayOnlyInLeapYears() {
        Recurrence yearly = new Recurrence(Frequency.YEARLY, 1, null, null, null);
        LocalDate leapDay = LocalDate.of(2028, 2, 29);

        assertThat(yearly.dates(leapDay, leapDay, LocalDate.of(2033, 12, 31)))
                .containsExactly(leapDay, LocalDate.of(2032, 2, 29));
    }

    @Test
    void firstDateIsEmptyWhenTheRuleEndsBeforeItsFirstTerm() {
        // Montag als Beginn, nur freitags, aber Ende schon am Donnerstag
        Recurrence fridaysUntilThursday = new Recurrence(Frequency.WEEKLY, 1, Set.of(FRIDAY), day(11, 5), null);

        assertThat(fridaysUntilThursday.firstDate(day(11, 2))).isEmpty();
        assertThat(new Recurrence(Frequency.WEEKLY, 1, Set.of(FRIDAY), null, null).firstDate(day(11, 2)))
                .contains(day(11, 6));
    }

    @Test
    void endingBeforeADayKeepsTheRhythm() {
        Recurrence weekly = new Recurrence(Frequency.WEEKLY, 1, null, null, 10);

        Recurrence ended = weekly.endingBefore(day(11, 17));

        assertThat(ended.dates(TUESDAY_3RD, TUESDAY_3RD, END_OF_YEAR)).containsExactly(day(11, 3), day(11, 10));
        assertThat(ended.count()).isNull();
    }

    @Test
    void describesTheRuleInGerman() {
        assertThat(new Recurrence(Frequency.WEEKLY, 1, Set.of(THURSDAY, TUESDAY), LocalDate.of(2027, 7, 31), null)
                .describe(TUESDAY_3RD)).isEqualTo("jeden Dienstag und Donnerstag bis 31.07.2027");
        assertThat(new Recurrence(Frequency.WEEKLY, 2, null, null, null).describe(TUESDAY_3RD))
                .isEqualTo("alle 2 Wochen am Dienstag");
        assertThat(new Recurrence(Frequency.DAILY, 1, null, null, 10).describe(TUESDAY_3RD))
                .isEqualTo("täglich, 10-mal");
        assertThat(new Recurrence(Frequency.MONTHLY, 1, null, null, null).describe(day(11, 15)))
                .isEqualTo("jeden Monat am 15.");
        assertThat(new Recurrence(Frequency.YEARLY, 1, null, null, null).describe(LocalDate.of(2028, 2, 29)))
                .isEqualTo("jedes Jahr am 29. Februar");
    }

    @Test
    void validationRejectsContradictoryRules() {
        assertThat(new Recurrence(Frequency.WEEKLY, 1, null, day(12, 1), 5).isEndUnambiguous()).isFalse();
        assertThat(new Recurrence(Frequency.MONTHLY, 1, Set.of(TUESDAY), null, null).isWeekdaysOnlyWeekly()).isFalse();
        assertThat(new Recurrence(Frequency.WEEKLY, 1, Set.of(TUESDAY), null, 5).isEndUnambiguous()).isTrue();
    }
}
