package de.familyhub.weather;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import de.familyhub.weather.OpenWeatherApi.City;
import de.familyhub.weather.OpenWeatherApi.Condition;
import de.familyhub.weather.OpenWeatherApi.Current;
import de.familyhub.weather.OpenWeatherApi.Forecast;
import de.familyhub.weather.OpenWeatherApi.Main;
import de.familyhub.weather.OpenWeatherApi.Slot;
import de.familyhub.weather.OpenWeatherApi.Wind;
import de.familyhub.weather.WeatherReport.AdviceItem;

// Tageswerte und Kleidungsempfehlung aus aktuellem Wetter und 3-Stunden-Vorhersage (Ortszeit Sommerzeit, UTC+2).
class WeatherReportsTest {

    private static final int CEST = 7200;
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 28, 9, 0);

    private static long epoch(LocalDateTime local) {
        return local.toEpochSecond(ZoneOffset.ofTotalSeconds(CEST));
    }

    private static Condition cloudy() {
        return new Condition(803, "Clouds", "überwiegend bewölkt", "04d");
    }

    private static Current current(double temp, Condition condition) {
        return new Current(epoch(NOW), CEST, "Stuttgart", new Main(temp, temp - 2, temp, temp, 70),
                List.of(condition), new Wind(5));
    }

    private static Slot slot(LocalDateTime start, double min, double max, Condition condition, double pop) {
        return new Slot(epoch(start), new Main((min + max) / 2, min, min, max, 60), List.of(condition), new Wind(3), pop);
    }

    // Heute ab 12 Uhr und die nächsten fünf Tage, jeweils 3-Stunden-Zeiträume mit 12..20 Grad
    private static List<Slot> week() {
        List<Slot> slots = new ArrayList<>();
        for (LocalDateTime t = NOW.withHour(12); t.isBefore(NOW.plusDays(6).withHour(0)); t = t.plusHours(3)) {
            int hour = t.getHour();
            double max = hour >= 12 && hour <= 15 ? 20 : 14;
            slots.add(slot(t, max - 2, max, new Condition(803, "Clouds", "bewölkt", hour >= 21 || hour < 6 ? "04n" : "04d"),
                    0.1));
        }
        return slots;
    }

    @Test
    void todayCombinesCurrentAndForecastAndDaysAreGroupedInLocalTime() {
        List<Slot> slots = week();
        // Regen am Mittwoch um 15 Uhr mit 80 %
        slots.add(slot(LocalDateTime.of(2026, 9, 30, 15, 0), 10, 11, new Condition(500, "Rain", "leichter Regen", "10d"),
                0.8));

        WeatherReport report = WeatherReports.build("Stuttgart", current(9.6, cloudy()),
                new Forecast(slots, new City("Stuttgart", "DE", CEST)));

        assertThat(report.location()).isEqualTo("Stuttgart");
        assertThat(report.current().temp()).isEqualTo(10);
        assertThat(report.current().description()).isEqualTo("Überwiegend bewölkt");
        assertThat(report.current().wind()).isEqualTo(18);
        assertThat(report.high()).isEqualTo(20);
        assertThat(report.low()).isEqualTo(10);
        assertThat(report.days()).extracting(WeatherReport.Day::date).containsExactly(
                LocalDate.of(2026, 9, 29), LocalDate.of(2026, 9, 30), LocalDate.of(2026, 10, 1),
                LocalDate.of(2026, 10, 2));
        assertThat(report.days().get(0)).satisfies(d -> {
            assertThat(d.high()).isEqualTo(20);
            assertThat(d.low()).isEqualTo(12);
            assertThat(d.icon()).isEqualTo("04d");
            assertThat(d.rainChance()).isEqualTo(10);
        });
        assertThat(report.days().get(1).rainChance()).isEqualTo(80);
    }

    @Test
    void rainLaterTodayRecommendsRainGearWithTheStartTime() {
        List<Slot> slots = week();
        slots.add(slot(NOW.withHour(15), 14, 15, new Condition(501, "Rain", "mäßiger Regen", "10d"), 0.9));
        // Regenwahrscheinlichkeit zu gering: zählt nicht
        slots.add(slot(NOW.withHour(12), 15, 16, new Condition(500, "Rain", "leichter Regen", "10d"), 0.2));

        WeatherReport report = WeatherReports.build("Stuttgart", current(12, cloudy()),
                new Forecast(slots, new City("Stuttgart", "DE", CEST)));

        assertThat(report.advice().kind()).isEqualTo("rain");
        assertThat(report.advice().items()).first().satisfies(i -> {
            assertThat(i.label()).isEqualTo("Regenschirm");
            assertThat(i.reason()).isEqualTo("Regen ab 15 Uhr");
        });
    }

    @Test
    void rainingNowSaysSo() {
        WeatherReport report = WeatherReports.build("Stuttgart",
                current(12, new Condition(500, "Rain", "leichter Regen", "10d")),
                new Forecast(week(), new City("Stuttgart", "DE", CEST)));

        assertThat(report.advice().items()).first().extracting(AdviceItem::reason).isEqualTo("Es regnet gerade");
    }

    private static WeatherReport.Current now(int temp, int feelsLike, int wind) {
        return new WeatherReport.Current(temp, feelsLike, "", "04d", 60, wind);
    }

    @Test
    void coldHeatSunAndMildFollowTheTemperatures() {
        assertThat(WeatherReports.advice(cloudy(), now(2, -1, 25), 4, -1, (LocalTime) null))
                .satisfies(a -> {
                    assertThat(a.kind()).isEqualTo("cold");
                    assertThat(a.items()).extracting(AdviceItem::label)
                            .containsExactly("Winterjacke", "Schal und Mütze", "Handschuhe");
                    assertThat(a.items().get(1).reason()).isEqualTo("Kalter Wind, 25 km/h");
                });
        assertThat(WeatherReports.advice(cloudy(), now(31, 33, 5), 33, 20, null).kind()).isEqualTo("heat");
        assertThat(WeatherReports.advice(new Condition(800, "Clear", "klarer Himmel", "01d"), now(24, 24, 5), 25, 13,
                null).kind()).isEqualTo("sun");
        // Warm, aber bewölkt (so am 28.09.2026 in Stuttgart): keine Jacke, außer es ist morgens kühl
        assertThat(WeatherReports.advice(cloudy(), now(28, 28, 3), 28, 17, null))
                .satisfies(a -> {
                    assertThat(a.kind()).isEqualTo("warm");
                    assertThat(a.items()).extracting(AdviceItem::label)
                            .containsExactly("Leichte Kleidung", "Trinkflasche");
                });
        assertThat(WeatherReports.advice(cloudy(), now(24, 24, 3), 25, 11, null).items())
                .extracting(AdviceItem::label).contains("Dünne Jacke für morgens");
        assertThat(WeatherReports.advice(cloudy(), now(18, 18, 5), 20, 11, null))
                .satisfies(a -> {
                    assertThat(a.kind()).isEqualTo("mild");
                    assertThat(a.items()).extracting(AdviceItem::label)
                            .containsExactly("Leichte Jacke", "Zwiebellook", "Feste Schuhe");
                    assertThat(a.items().get(0).reason()).isEqualTo("Zwischen 11° und 20°");
                });
    }
}
