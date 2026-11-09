package de.familyhub.calendar;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.MonthDay;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

// Regel, nach der sich ein Termin wiederholt (Entscheidung vom 03.11.2026): täglich, wöchentlich an einem oder
// mehreren Wochentagen, monatlich am selben Tag oder jährlich, jeweils auch "alle n". Ende an einem Datum, nach einer
// Anzahl oder nie. Wie bei Google entfallen Tage, die es nicht gibt: monatlich am 31. überspringt kürzere Monate,
// jährlich am 29. Februar die Jahre ohne Schalttag. Gezählt (count) werden nur Tage, die es gibt.
public record Recurrence(
        @NotNull(message = "Rhythmus ist Pflicht")
        @Schema(description = "daily, weekly, monthly oder yearly")
        Frequency frequency,

        @Min(value = 1, message = "Abstand muss mindestens 1 sein")
        @Max(value = 99, message = "Abstand darf höchstens 99 sein")
        @Schema(description = "Alle wie viele Tage, Wochen, Monate oder Jahre; ohne Angabe 1")
        Integer interval,

        @Schema(description = "Nur wöchentlich: Wochentage, z. B. [\"TUESDAY\", \"THURSDAY\"]; ohne Angabe der "
                + "Wochentag des Beginns")
        Set<DayOfWeek> weekdays,

        @Schema(description = "Letzter möglicher Tag (einschließlich); ohne Angabe kein Enddatum")
        LocalDate until,

        @Min(value = 1, message = "Anzahl muss mindestens 1 sein")
        @Max(value = 999, message = "Anzahl darf höchstens 999 sein")
        @Schema(description = "Anzahl der Termine; ohne Angabe keine Begrenzung")
        Integer count) {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.yyyy");

    public Recurrence {
        interval = interval == null ? 1 : interval;
        weekdays = weekdays == null || weekdays.stream().noneMatch(Objects::nonNull) ? Set.of()
                : Collections.unmodifiableSet(EnumSet.copyOf(weekdays.stream().filter(Objects::nonNull).toList()));
    }

    @JsonIgnore
    @AssertTrue(message = "Ende entweder mit Datum oder mit Anzahl angeben, nicht beides")
    public boolean isEndUnambiguous() {
        return until == null || count == null;
    }

    @JsonIgnore
    @AssertTrue(message = "Wochentage gibt es nur bei wöchentlicher Wiederholung")
    public boolean isWeekdaysOnlyWeekly() {
        return weekdays.isEmpty() || frequency == Frequency.WEEKLY;
    }

    // Dieselbe Regel, die spätestens am Tag vor day endet (für "diesen und alle folgenden")
    public Recurrence endingBefore(LocalDate day) {
        return new Recurrence(frequency, interval, weekdays, day.minusDays(1), null);
    }

    public Recurrence withCount(int newCount) {
        return new Recurrence(frequency, interval, weekdays, null, newCount);
    }

    // Termintage der Regel, die in [from, to] liegen. Die Serie beginnt am Tag first: Er zählt nur, wenn er zur Regel
    // passt (z. B. einer der Wochentage ist), sonst beginnt sie am nächsten passenden Tag. Gezählt wird immer ab first.
    public List<LocalDate> dates(LocalDate first, LocalDate from, LocalDate to) {
        return dates(first, from, to, Integer.MAX_VALUE);
    }

    public Optional<LocalDate> firstDate(LocalDate first) {
        return dates(first, first, first.plusYears(400), 1).stream().findFirst();
    }

    public List<LocalDate> dates(LocalDate first, LocalDate from, LocalDate to, int limit) {
        LocalDate last = until != null && until.isBefore(to) ? until : to;
        List<LocalDate> result = new ArrayList<>();
        int number = 0;
        for (long period = 0; !periodStart(first, period).isAfter(last); period++) {
            for (LocalDate date : candidates(first, period)) {
                if (date.isBefore(first)) {
                    continue;
                }
                if (date.isAfter(last)) {
                    return result;
                }
                number++;
                if (count != null && number > count) {
                    return result;
                }
                if (!date.isBefore(from)) {
                    result.add(date);
                    if (result.size() >= limit) {
                        return result;
                    }
                }
            }
        }
        return result;
    }

    // Erster Tag des period-ten Abschnitts (Tag, Woche ab Montag, Monat, Jahr), aufsteigend
    private LocalDate periodStart(LocalDate first, long period) {
        long steps = period * interval;
        return switch (frequency) {
            case DAILY -> first.plusDays(steps);
            case WEEKLY -> first.with(DayOfWeek.MONDAY).plusWeeks(steps);
            case MONTHLY -> YearMonth.from(first).plusMonths(steps).atDay(1);
            case YEARLY -> first.withDayOfYear(1).plusYears(steps);
        };
    }

    // Die Termintage eines Abschnitts in zeitlicher Reihenfolge; leer, wenn es den Tag dort nicht gibt
    private List<LocalDate> candidates(LocalDate first, long period) {
        LocalDate start = periodStart(first, period);
        return switch (frequency) {
            case DAILY -> List.of(start);
            case WEEKLY -> effectiveWeekdays(first).stream().map(day -> start.plusDays(day.getValue() - 1L)).toList();
            case MONTHLY -> {
                YearMonth month = YearMonth.from(start);
                yield month.isValidDay(first.getDayOfMonth()) ? List.of(month.atDay(first.getDayOfMonth())) : List.of();
            }
            case YEARLY -> {
                MonthDay day = MonthDay.from(first);
                yield day.isValidYear(start.getYear()) ? List.of(day.atYear(start.getYear())) : List.of();
            }
        };
    }

    private Set<DayOfWeek> effectiveWeekdays(LocalDate first) {
        return weekdays.isEmpty() ? EnumSet.of(first.getDayOfWeek()) : weekdays;
    }

    // Für Benachrichtigungen, z. B. "jeden Dienstag und Donnerstag bis 31.07.2027"
    public String describe(LocalDate first) {
        String rhythm = switch (frequency) {
            case DAILY -> interval == 1 ? "täglich" : "alle " + interval + " Tage";
            case WEEKLY -> (interval == 1 ? "jeden " : "alle " + interval + " Wochen am ") + weekdayNames(first);
            case MONTHLY -> (interval == 1 ? "jeden Monat" : "alle " + interval + " Monate") + " am "
                    + first.getDayOfMonth() + ".";
            case YEARLY -> (interval == 1 ? "jedes Jahr" : "alle " + interval + " Jahre") + " am "
                    + first.getDayOfMonth() + ". " + first.getMonth().getDisplayName(TextStyle.FULL, Locale.GERMAN);
        };
        if (until != null) {
            return rhythm + " bis " + until.format(DATE);
        }
        return count == null ? rhythm : rhythm + ", " + count + "-mal";
    }

    private String weekdayNames(LocalDate first) {
        List<String> names = effectiveWeekdays(first).stream()
                .map(day -> day.getDisplayName(TextStyle.FULL, Locale.GERMAN)).toList();
        if (names.size() == 1) {
            return names.get(0);
        }
        return names.subList(0, names.size() - 1).stream().collect(Collectors.joining(", "))
                + " und " + names.get(names.size() - 1);
    }
}
