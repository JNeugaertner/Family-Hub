package de.familyhub.weather;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// Wohnort der Familie für das Wetter, wie ihn die Ortssuche von OpenWeather liefert.
public record WeatherLocation(
        @NotBlank(message = "Ort darf nicht leer sein")
        @Size(max = 100, message = "Ort darf höchstens 100 Zeichen lang sein")
        String name,

        @Size(max = 100) @Schema(description = "Bundesland, falls bekannt") String state,

        @Size(max = 2) @Schema(description = "Ländercode, z. B. DE") String country,

        @NotNull(message = "Breitengrad ist Pflicht")
        @DecimalMin(value = "-90", message = "Ungültiger Breitengrad")
        @DecimalMax(value = "90", message = "Ungültiger Breitengrad")
        Double lat,

        @NotNull(message = "Längengrad ist Pflicht")
        @DecimalMin(value = "-180", message = "Ungültiger Längengrad")
        @DecimalMax(value = "180", message = "Ungültiger Längengrad")
        Double lon) {
}
