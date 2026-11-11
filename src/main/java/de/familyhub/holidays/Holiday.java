package de.familyhub.holidays;

import java.time.LocalDate;
import java.time.MonthDay;
import java.util.function.IntFunction;

// Feiertage mit Countdown (Roadmap 0.9, Entscheidungen vom 11.11.2026): die Auswahl aus der Planung, bundesweit
// gleich, darum ohne Einstellung je Bundesland. Weihnachten zählt bis Heiligabend, weil Familien darauf hinfiebern.
// FamilyHub rechnet die Tage jedes Jahr neu aus, es gibt nichts zu speichern oder nachts zu verlängern.
public enum Holiday {

    NEUJAHR("Neujahr", "🎆", fixed(1, 1)),
    OSTERN("Ostern", "🐣", Holiday::easterSunday),
    CHRISTI_HIMMELFAHRT("Christi Himmelfahrt", "☁️", year -> easterSunday(year).plusDays(39)),
    PFINGSTEN("Pfingsten", "🕊️", year -> easterSunday(year).plusDays(49)),
    TAG_DER_DEUTSCHEN_EINHEIT("Tag der Deutschen Einheit", "🇩🇪", fixed(10, 3)),
    WEIHNACHTEN("Weihnachten", "🎄", fixed(12, 24));

    private final String title;
    private final String emoji;
    private final IntFunction<LocalDate> dateInYear;

    Holiday(String title, String emoji, IntFunction<LocalDate> dateInYear) {
        this.title = title;
        this.emoji = emoji;
        this.dateInYear = dateInYear;
    }

    public String title() {
        return title;
    }

    public String emoji() {
        return emoji;
    }

    public LocalDate in(int year) {
        return dateInYear.apply(year);
    }

    private static IntFunction<LocalDate> fixed(int month, int day) {
        return MonthDay.of(month, day)::atYear;
    }

    // Ostersonntag im gregorianischen Kalender nach der Gaußschen Osterformel in der Form von Meeus/Jones/Butcher;
    // gilt für alle Jahre ab 1583. Himmelfahrt (+39) und Pfingsten (+49) hängen daran.
    static LocalDate easterSunday(int year) {
        int a = year % 19;
        int b = year / 100;
        int c = year % 100;
        int d = b / 4;
        int e = b % 4;
        int f = (b + 8) / 25;
        int g = (b - f + 1) / 3;
        int h = (19 * a + b - d - g + 15) % 30;
        int i = c / 4;
        int k = c % 4;
        int l = (32 + 2 * e + 2 * i - h - k) % 7;
        int m = (a + 11 * h + 22 * l) / 451;
        int month = (h + l - 7 * m + 114) / 31;
        int day = (h + l - 7 * m + 114) % 31 + 1;
        return LocalDate.of(year, month, day);
    }
}
