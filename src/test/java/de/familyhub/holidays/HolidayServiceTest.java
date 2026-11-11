package de.familyhub.holidays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class HolidayServiceTest {

    private static final ZoneId BERLIN = ZoneId.of("Europe/Berlin");

    private static HolidayService on(LocalDate today) {
        // kurz nach Mitternacht deutscher Zeit, damit der Tag nicht an der UTC-Grenze kippt
        return new HolidayService(Clock.fixed(today.atTime(0, 5).atZone(BERLIN).toInstant(), BERLIN));
    }

    // Ostersonntage aus Kalendern; 1818 und 2285 sind die frühesten (22.03.), 1943 und 2038 die spätesten (25.04.)
    @ParameterizedTest
    @CsvSource({ "1818-03-22", "1943-04-25", "2000-04-23", "2024-03-31", "2025-04-20", "2026-04-05",
            "2027-03-28", "2028-04-16", "2038-04-25", "2285-03-22" })
    void easterSundayMatchesTheCalendar(LocalDate expected) {
        assertThat(Holiday.easterSunday(expected.getYear())).isEqualTo(expected);
    }

    @Test
    void holidaysOf2026() {
        assertThat(on(LocalDate.of(2026, 1, 1)).between(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 12, 31)))
                .extracting(HolidayDate::key, HolidayDate::date)
                .containsExactly(
                        tuple("neujahr", LocalDate.of(2026, 1, 1)),
                        tuple("ostern", LocalDate.of(2026, 4, 5)),
                        tuple("christi_himmelfahrt", LocalDate.of(2026, 5, 14)),
                        tuple("pfingsten", LocalDate.of(2026, 5, 24)),
                        tuple("tag_der_deutschen_einheit", LocalDate.of(2026, 10, 3)),
                        tuple("weihnachten", LocalDate.of(2026, 12, 24)));
    }

    @Test
    void upcomingCountsDownAndRollsOverIntoTheNextYear() {
        assertThat(on(LocalDate.of(2026, 11, 11)).upcoming(3))
                .extracting(HolidayDate::name, HolidayDate::date, HolidayDate::daysLeft)
                .containsExactly(
                        tuple("Weihnachten", LocalDate.of(2026, 12, 24), 43L),
                        tuple("Neujahr", LocalDate.of(2027, 1, 1), 51L),
                        tuple("Ostern", LocalDate.of(2027, 3, 28), 137L));
    }

    @Test
    void aHolidayTodayIsFirstWithZeroDaysAndYesterdaysIsGone() {
        assertThat(on(LocalDate.of(2026, 12, 24)).upcoming(1)).singleElement()
                .satisfies(h -> {
                    assertThat(h.key()).isEqualTo("weihnachten");
                    assertThat(h.daysLeft()).isZero();
                });
        assertThat(on(LocalDate.of(2026, 12, 25)).upcoming(1)).singleElement()
                .extracting(HolidayDate::key).isEqualTo("neujahr");
    }

    @Test
    void allSixComeWithinAYear() {
        assertThat(on(LocalDate.of(2026, 6, 1)).upcoming(6)).extracting(HolidayDate::key)
                .containsExactly("tag_der_deutschen_einheit", "weihnachten", "neujahr", "ostern",
                        "christi_himmelfahrt", "pfingsten");
    }

    @Test
    void pastHolidaysInARangeHaveNegativeDaysLeft() {
        assertThat(on(LocalDate.of(2026, 11, 11)).between(LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)))
                .singleElement().extracting(HolidayDate::daysLeft).isEqualTo(-39L);
    }
}
