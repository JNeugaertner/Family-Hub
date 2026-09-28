package de.familyhub.meals;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.mongodb.core.mapping.Document;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Gericht der Gerichte-Sammlung: einmal mit Zutaten anlegen, dann im Wochenplan auswählen.
@Document("dishes")
public record Dish(
        @Id @Schema(accessMode = Schema.AccessMode.READ_ONLY) String id,

        @NotBlank(message = "Name darf nicht leer sein")
        @Size(max = 60, message = "Name darf höchstens 60 Zeichen lang sein")
        String name,

        @Valid
        @Size(max = 30, message = "Höchstens 30 Zutaten")
        List<Ingredient> ingredients,

        @Schema(accessMode = Schema.AccessMode.READ_ONLY) String createdBy,
        @Schema(accessMode = Schema.AccessMode.READ_ONLY) LocalDateTime createdAt) {

    @PersistenceCreator
    public Dish {
        ingredients = ingredients == null ? List.of() : ingredients.stream().filter(Objects::nonNull).toList();
    }

    Dish withContent(Dish changed) {
        return new Dish(id, changed.name().strip(), changed.ingredients().stream().map(Ingredient::normalized).toList(),
                createdBy, createdAt);
    }
}
