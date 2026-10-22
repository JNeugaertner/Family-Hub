package de.familyhub.meals;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.swagger.v3.oas.annotations.media.Schema;

// Eintrag im Wochenplan: ein Gericht aus der Sammlung (dishId) oder Freitext. name ist bei Gerichten eine Kopie des
// Gerichtnamens, damit der Plan lesbar bleibt, wenn das Gericht gelöscht wird.
@Document("meals")
@CompoundIndex(def = "{'date': 1, 'type': 1}")
public record MealEntry(
        @Id String id,
        LocalDate date,
        MealType type,
        @Schema(description = "Gericht aus der Sammlung; leer bei Freitext") String dishId,
        String name,
        @Schema(description = "approved oder proposed (Wunsch, wartet auf einen Administrator)") MealStatus status,
        String createdBy,
        LocalDateTime createdAt,
        @Schema(description = "Über den KI-Assistenten entstanden: ein Mensch hat einen Entwurf übernommen")
        Boolean viaAssistant) {

    @PersistenceCreator
    public MealEntry {
        status = status == null ? MealStatus.APPROVED : status;
        viaAssistant = Boolean.TRUE.equals(viaAssistant);
    }

    public MealEntry(String id, LocalDate date, MealType type, String dishId, String name, MealStatus status,
            String createdBy, LocalDateTime createdAt) {
        this(id, date, type, dishId, name, status, createdBy, createdAt, false);
    }

    @JsonIgnore
    public boolean isProposal() {
        return status == MealStatus.PROPOSED;
    }

    MealEntry withDish(String newDishId, String newName) {
        return new MealEntry(id, date, type, newDishId, newName, status, createdBy, createdAt, viaAssistant);
    }

    MealEntry approved() {
        return new MealEntry(id, date, type, dishId, name, MealStatus.APPROVED, createdBy, createdAt, viaAssistant);
    }
}
