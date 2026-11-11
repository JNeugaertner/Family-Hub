package de.familyhub.holidays;

import java.time.Clock;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

import org.springframework.stereotype.Service;

@Service
public class HolidayService {

    private final Clock clock;

    public HolidayService(Clock clock) {
        this.clock = clock;
    }

    // Alle Feiertage von from bis to (beide eingeschlossen), nach Datum
    public List<HolidayDate> between(LocalDate from, LocalDate to) {
        LocalDate today = LocalDate.now(clock);
        List<HolidayDate> result = new ArrayList<>();
        for (int year = from.getYear(); year <= to.getYear(); year++) {
            for (Holiday holiday : Holiday.values()) {
                LocalDate date = holiday.in(year);
                if (!date.isBefore(from) && !date.isAfter(to)) {
                    result.add(toDate(holiday, date, today));
                }
            }
        }
        result.sort(Comparator.comparing(HolidayDate::date));
        return result;
    }

    // Die nächsten Feiertage ab heute; ein Feiertag heute zählt mit ("Heute ist Weihnachten"). Ein Jahr reicht immer,
    // weil jeder Feiertag einmal im Jahr kommt.
    public List<HolidayDate> upcoming(int count) {
        LocalDate today = LocalDate.now(clock);
        return between(today, today.plusYears(1)).stream().limit(count).toList();
    }

    private static HolidayDate toDate(Holiday holiday, LocalDate date, LocalDate today) {
        return new HolidayDate(holiday.name().toLowerCase(Locale.ROOT), holiday.title(), holiday.emoji(), date,
                ChronoUnit.DAYS.between(today, date));
    }
}
