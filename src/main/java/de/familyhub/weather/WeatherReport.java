package de.familyhub.weather;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

// Wetter für die Kachel auf der Übersicht. Temperaturen in °C (gerundet), Wind in km/h.
public record WeatherReport(
        @Schema(description = "Wohnort, z. B. Stuttgart") String location,
        Current current,
        @Schema(description = "Höchstwert heute") int high,
        @Schema(description = "Tiefstwert heute") int low,
        @Schema(description = "Die nächsten Tage (ohne heute), höchstens vier") List<Day> days,
        Advice advice,
        @Schema(description = "Zeitpunkt der Messung bei OpenWeather") Instant observedAt) {

    // icon: Symbol-Code von OpenWeather, z. B. "10d" (Regen, Tag)
    public record Current(int temp, int feelsLike, String description, String icon, int humidity, int wind) {
    }

    // rainChance: höchste Regenwahrscheinlichkeit des Tages in Prozent
    public record Day(LocalDate date, int high, int low, String description, String icon, int rainChance) {
    }

    @Schema(description = "Kleidungsempfehlung für heute")
    public record Advice(
            @Schema(description = "rain, heat, cold, sun oder mild") String kind,
            String title,
            List<AdviceItem> items) {
    }

    public record AdviceItem(String icon, String label, String reason) {
    }
}
