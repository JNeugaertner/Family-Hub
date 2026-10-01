package de.familyhub.meals;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.mongodb.core.mapping.Document;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Gericht der Gerichte-Sammlung: einmal mit Zutaten (und auf Wunsch Kochanleitung) anlegen, dann im Wochenplan
// auswählen. Kochanleitung, Zubereitungszeit und Portionen sind optional (Entscheidung vom 30.09.2026).
@Document("dishes")
public record Dish(
        @Id @Schema(accessMode = Schema.AccessMode.READ_ONLY) String id,

        @NotBlank(message = "Name darf nicht leer sein")
        @Size(max = 60, message = "Name darf höchstens 60 Zeichen lang sein")
        String name,

        @Valid
        @Size(max = 30, message = "Höchstens 30 Zutaten")
        List<Ingredient> ingredients,

        @Size(max = 4000, message = "Kochanleitung darf höchstens 4000 Zeichen lang sein")
        @Schema(description = "Kochanleitung, ein Schritt pro Zeile")
        String instructions,

        @Min(value = 1, message = "Zubereitungszeit muss mindestens 1 Minute sein")
        @Max(value = 1440, message = "Zubereitungszeit darf höchstens 24 Stunden sein")
        @Schema(description = "Zubereitungszeit in Minuten")
        Integer prepMinutes,

        @Min(value = 1, message = "Mindestens 1 Portion")
        @Max(value = 50, message = "Höchstens 50 Portionen")
        @Schema(description = "Für wie viele Personen die Zutatenmengen gedacht sind (nur Anzeige)")
        Integer servings,

        @Schema(accessMode = Schema.AccessMode.READ_ONLY) String createdBy,
        @Schema(accessMode = Schema.AccessMode.READ_ONLY) LocalDateTime createdAt) {

    @PersistenceCreator
    public Dish {
        ingredients = ingredients == null ? List.of() : ingredients.stream().filter(Objects::nonNull).toList();
    }

    // Gericht ohne Kochanleitung, Zubereitungszeit und Portionen
    public Dish(String id, String name, List<Ingredient> ingredients, String createdBy, LocalDateTime createdAt) {
        this(id, name, ingredients, null, null, null, createdBy, createdAt);
    }

    Dish withContent(Dish changed) {
        return new Dish(id, changed.name().strip(), changed.ingredients().stream().map(Ingredient::normalized).toList(),
                cleanInstructions(changed.instructions()), changed.prepMinutes(), changed.servings(), createdBy, createdAt);
    }

    // Eine Zeile je Schritt: Leerzeilen und Leerzeichen am Rand weg, Zeilenenden vereinheitlicht; leer wird null
    static String cleanInstructions(String text) {
        if (text == null) {
            return null;
        }
        String cleaned = text.lines().map(String::strip).filter(line -> !line.isEmpty())
                .collect(Collectors.joining("\n"));
        return cleaned.isEmpty() ? null : cleaned;
    }
}
