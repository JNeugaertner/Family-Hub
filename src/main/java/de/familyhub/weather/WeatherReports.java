package de.familyhub.weather;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import de.familyhub.weather.OpenWeatherApi.Condition;
import de.familyhub.weather.OpenWeatherApi.Current;
import de.familyhub.weather.OpenWeatherApi.Forecast;
import de.familyhub.weather.OpenWeatherApi.Slot;
import de.familyhub.weather.WeatherReport.Advice;
import de.familyhub.weather.WeatherReport.AdviceItem;
import de.familyhub.weather.WeatherReport.Day;

// Baut aus aktuellem Wetter und 3-Stunden-Vorhersage die Kachel: Tageswerte je Tag in Ortszeit des Wohnorts und
// eine Kleidungsempfehlung nach festen Regeln (Entscheidung vom 28.09.2026).
final class WeatherReports {

    static final int FORECAST_DAYS = 4;

    // Ab dieser Regenwahrscheinlichkeit gilt ein Zeitraum als regnerisch
    static final double RAIN_CHANCE = 0.4;

    private WeatherReports() {
    }

    static WeatherReport build(String location, Current current, Forecast forecast) {
        ZoneOffset offset = ZoneOffset.ofTotalSeconds(current.timezone());
        LocalDateTime now = LocalDateTime.ofEpochSecond(current.dt(), 0, offset);
        LocalDate today = now.toLocalDate();

        Map<LocalDate, List<Slot>> byDay = new TreeMap<>();
        for (Slot slot : forecast.list()) {
            byDay.computeIfAbsent(localTime(slot, offset).toLocalDate(), d -> new ArrayList<>()).add(slot);
        }
        List<Slot> todaySlots = byDay.getOrDefault(today, List.of());

        double high = todaySlots.stream().mapToDouble(s -> s.main().tempMax()).max().orElse(current.main().temp());
        double low = todaySlots.stream().mapToDouble(s -> s.main().tempMin()).min().orElse(current.main().temp());
        high = Math.max(high, current.main().temp());
        low = Math.min(low, current.main().temp());

        List<Day> days = byDay.entrySet().stream()
                .filter(e -> e.getKey().isAfter(today))
                .limit(FORECAST_DAYS)
                .map(e -> day(e.getKey(), e.getValue(), offset))
                .toList();

        Condition condition = first(current.weather());
        int windKmh = kmh(current.wind() == null ? 0 : current.wind().speed());
        WeatherReport.Current currentReport = new WeatherReport.Current(round(current.main().temp()),
                round(current.main().feelsLike()), capitalize(condition.description()), condition.icon(),
                current.main().humidity(), windKmh);

        LocalTime rainFrom = rainFrom(todaySlots, now, offset);
        Advice advice = advice(condition, currentReport, round(high), round(low), rainFrom);
        return new WeatherReport(location, currentReport, round(high), round(low), days, advice,
                Instant.ofEpochSecond(current.dt()));
    }

    // Wann es heute voraussichtlich zu regnen beginnt (null: kein Regen erwartet)
    private static LocalTime rainFrom(List<Slot> todaySlots, LocalDateTime now, ZoneOffset offset) {
        return todaySlots.stream()
                .filter(s -> isRain(first(s.weather())) && (s.pop() == null || s.pop() >= RAIN_CHANCE))
                .map(s -> localTime(s, offset))
                // Ein 3-Stunden-Zeitraum, der schon begonnen hat, zählt ab jetzt
                .filter(t -> t.plusHours(3).isAfter(now))
                .min(Comparator.naturalOrder())
                .map(t -> t.isBefore(now) ? now.toLocalTime() : t.toLocalTime())
                .orElse(null);
    }

    static Advice advice(Condition condition, WeatherReport.Current current, int high, int low, LocalTime rainFrom) {
        boolean rainingNow = isRain(condition);
        if (rainingNow || rainFrom != null) {
            String when = rainingNow ? "Es regnet gerade" : "Regen ab " + rainFrom.getHour() + " Uhr";
            List<AdviceItem> items = new ArrayList<>(List.of(
                    new AdviceItem("☂️", "Regenschirm", when),
                    new AdviceItem("🧥", "Regenjacke", current.wind() >= 20 ? "Wind bis " + current.wind() + " km/h"
                            : "Bleibt trocken darunter"),
                    new AdviceItem("🥾", "Wasserfeste Schuhe", "Nasse Wege")));
            if (low <= 5) {
                items.add(new AdviceItem("🧣", "Schal und Mütze", "Nur " + low + "° am kältesten"));
            }
            return new Advice("rain", "Regenausrüstung", items);
        }
        if (high >= 30) {
            return new Advice("heat", "Hitzeschutz", List.of(
                    new AdviceItem("👕", "Leichte Kleidung", "Bis " + high + "°"),
                    new AdviceItem("💧", "Viel trinken", "Mindestens 2 Liter"),
                    new AdviceItem("🧴", "Sonnencreme", "Starke Sonne")));
        }
        int coldest = Math.min(low, current.feelsLike());
        if (coldest <= 5) {
            List<AdviceItem> items = new ArrayList<>(List.of(
                    new AdviceItem("🧥", "Winterjacke", "Nur " + coldest + "° am kältesten"),
                    new AdviceItem("🧣", "Schal und Mütze", current.wind() >= 20 ? "Kalter Wind, "
                            + current.wind() + " km/h" : "Kalte Luft")));
            if (low <= 0) {
                items.add(new AdviceItem("🧤", "Handschuhe", "Frost möglich"));
            }
            return new Advice("cold", "Kälteschutz", items);
        }
        if (high >= 22 && condition.icon() != null && (condition.icon().startsWith("01")
                || condition.icon().startsWith("02"))) {
            return new Advice("sun", "Sonnenschutz", List.of(
                    new AdviceItem("🧴", "Sonnencreme", "Sonnig, bis " + high + "°"),
                    new AdviceItem("🧢", "Kappe oder Sonnenhut", "Direkte Sonne"),
                    new AdviceItem("💧", "Trinkflasche", "Warmer Tag")));
        }
        if (high >= 24) {
            List<AdviceItem> items = new ArrayList<>(List.of(
                    new AdviceItem("👕", "Leichte Kleidung", "Bis " + high + "°"),
                    new AdviceItem("💧", "Trinkflasche", "Warmer Tag")));
            if (low <= 12) {
                items.add(new AdviceItem("🧥", "Dünne Jacke für morgens", "Morgens nur " + low + "°"));
            }
            return new Advice("warm", "Warmes Wetter", items);
        }
        List<AdviceItem> items = new ArrayList<>(List.of(
                new AdviceItem("🧥", "Leichte Jacke", "Zwischen " + low + "° und " + high + "°")));
        if (high - low >= 8) {
            items.add(new AdviceItem("👕", "Zwiebellook", "Morgens kühl, später wärmer"));
        }
        items.add(new AdviceItem("👟", "Feste Schuhe", "Unterwegs gut gerüstet"));
        return new Advice("mild", "Wechselhaftes Wetter", items);
    }

    private static Day day(LocalDate date, List<Slot> slots, ZoneOffset offset) {
        double high = slots.stream().mapToDouble(s -> s.main().tempMax()).max().orElse(0);
        double low = slots.stream().mapToDouble(s -> s.main().tempMin()).min().orElse(0);
        // Symbol und Beschreibung vom Zeitraum, der am nächsten an 13 Uhr liegt (tagsüber)
        Slot midday = slots.stream()
                .min(Comparator.comparingInt(s -> Math.abs(localTime(s, offset).getHour() - 13)))
                .orElseThrow();
        Condition condition = first(midday.weather());
        int rainChance = (int) Math.round(slots.stream()
                .mapToDouble(s -> s.pop() == null ? 0 : s.pop()).max().orElse(0) * 100);
        String icon = condition.icon() == null ? null : condition.icon().replace('n', 'd');
        return new Day(date, round(high), round(low), capitalize(condition.description()), icon, rainChance);
    }

    // 2xx Gewitter, 3xx Niesel, 5xx Regen (Schnee ab 600 zählt nicht)
    private static boolean isRain(Condition condition) {
        return condition.id() >= 200 && condition.id() < 600;
    }

    private static LocalDateTime localTime(Slot slot, ZoneOffset offset) {
        return LocalDateTime.ofEpochSecond(slot.dt(), 0, offset);
    }

    private static Condition first(List<Condition> conditions) {
        return conditions == null || conditions.isEmpty() ? new Condition(800, "Clear", "", "01d") : conditions.get(0);
    }

    private static int round(double value) {
        return (int) Math.round(value);
    }

    // OpenWeather liefert m/s
    private static int kmh(double metersPerSecond) {
        return (int) Math.round(metersPerSecond * 3.6);
    }

    private static String capitalize(String text) {
        return text == null || text.isEmpty() ? "" : Character.toUpperCase(text.charAt(0)) + text.substring(1);
    }
}
