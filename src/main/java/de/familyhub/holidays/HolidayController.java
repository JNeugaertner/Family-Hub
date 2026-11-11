package de.familyhub.holidays;

import java.time.LocalDate;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.familyhub.web.ApiException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

// Feiertage sind keine Termine: Sie stehen nicht in der Datenbank, gehen nicht nach Google und lösen keine
// Benachrichtigungen aus. Sehen dürfen sie alle Angemeldeten, auch Gäste, weil nichts Persönliches darin steht.
@RestController
@RequestMapping("/api/holidays")
@Tag(name = "Feiertage")
public class HolidayController {

    // Reicht für jede Kalenderansicht samt Vor- und Folgemonat und begrenzt, was eine Anfrage berechnen lässt
    static final int MAX_DAYS = 400;
    static final int MAX_COUNT = Holiday.values().length;

    private final HolidayService holidays;

    public HolidayController(HolidayService holidays) {
        this.holidays = holidays;
    }

    @GetMapping
    @Operation(summary = "Feiertage in einem Zeitraum", description = "Neujahr, Ostern, Christi Himmelfahrt, "
            + "Pfingsten, Tag der Deutschen Einheit und Weihnachten (Heiligabend) von from bis to, beide "
            + "eingeschlossen, höchstens 400 Tage.")
    public List<HolidayDate> between(@RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        if (to.isBefore(from)) {
            throw ApiException.invalidField("to", "Das Ende darf nicht vor dem Anfang liegen.");
        }
        if (from.plusDays(MAX_DAYS).isBefore(to)) {
            throw ApiException.invalidField("to", "Höchstens " + MAX_DAYS + " Tage auf einmal.");
        }
        return holidays.between(from, to);
    }

    @GetMapping("/upcoming")
    @Operation(summary = "Countdown zu den nächsten Feiertagen", description = "Ab heute, der nächste zuerst; "
            + "daysLeft 0 heißt heute. count zwischen 1 und 6, Standard 3.")
    public List<HolidayDate> upcoming(@RequestParam(defaultValue = "3") int count) {
        if (count < 1 || count > MAX_COUNT) {
            throw ApiException.invalidField("count", "Bitte zwischen 1 und " + MAX_COUNT + " Feiertage abfragen.");
        }
        return holidays.upcoming(count);
    }
}
