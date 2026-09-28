package de.familyhub.rewards;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.mongodb.core.mapping.Document;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// Eine Belohnung im Shop, die man gegen Punkte einlösen kann. Nur aktive Belohnungen sind einlösbar.
@Document("rewards")
public record Reward(
        @Id @Schema(accessMode = Schema.AccessMode.READ_ONLY) String id,

        @NotBlank(message = "Emoji fehlt")
        @Size(max = 16, message = "Bitte nur ein Emoji")
        String emoji,

        @NotBlank(message = "Titel darf nicht leer sein")
        @Size(max = 60, message = "Titel darf höchstens 60 Zeichen lang sein")
        String name,

        @Size(max = 200, message = "Beschreibung darf höchstens 200 Zeichen lang sein")
        String description,

        @NotNull(message = "Punkte sind Pflicht")
        @Min(value = 1, message = "Eine Belohnung kostet mindestens 1 Punkt")
        @Max(value = 10000, message = "Höchstens 10000 Punkte")
        Integer cost,

        @NotNull(message = "Kategorie ist Pflicht")
        RewardCategory category,

        @Schema(description = "Nur aktive Belohnungen erscheinen im Shop (Standard: true)")
        Boolean active,

        @Schema(description = "Mehrfach einlösbar (Standard: true); sonst höchstens einmal je Person")
        Boolean repeatable) {

    @PersistenceCreator
    public Reward {
        active = active == null || active;
        repeatable = repeatable == null || repeatable;
    }

    public Reward withId(String newId) {
        return new Reward(newId, emoji, name, description, cost, category, active, repeatable);
    }
}
