package de.familyhub.ors;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// Terminort, wie ihn die Ortssuche von OpenRouteService liefert.
public record OrsLocation(
        @NotBlank(message = "Ort darf nicht leer sein")
        @Size(max = 100, message = "Ort darf höchstens 100 Zeichen lang sein")
        String label,

        @NotNull(message = "Breitengrad ist Pflicht")
        @DecimalMin(value = "-90", message = "Ungültiger Breitengrad")
        @DecimalMax(value = "90", message = "Ungültiger Breitengrad")
        Double lat,

        @NotNull(message = "Längengrad ist Pflicht")
        @DecimalMin(value = "-180", message = "Ungültiger Längengrad")
        @DecimalMax(value = "180", message = "Ungültiger Längengrad")
        Double lon) {
}
