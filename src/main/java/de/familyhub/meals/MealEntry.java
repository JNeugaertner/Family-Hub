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
        LocalDateTime createdAt) {

    @PersistenceCreator
    public MealEntry {
        status = status == null ? MealStatus.APPROVED : status;
    }

    @JsonIgnore
    public boolean isProposal() {
        return status == MealStatus.PROPOSED;
    }

    MealEntry withDish(String newDishId, String newName) {
        return new MealEntry(id, date, type, newDishId, newName, status, createdBy, createdAt);
    }

    MealEntry approved() {
        return new MealEntry(id, date, type, dishId, name, MealStatus.APPROVED, createdBy, createdAt);
    }
}
