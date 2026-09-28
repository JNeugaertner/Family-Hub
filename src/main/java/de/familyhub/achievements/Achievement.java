package de.familyhub.achievements;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import de.familyhub.task.TaskCategory;
import io.swagger.v3.oas.annotations.media.Schema;

// Ein Erfolg aus dem festen Katalog (AchievementCatalog). Eltern können ihn aktivieren oder deaktivieren und
// Ziel und Bonus anpassen; Regel, Name und Symbol bleiben.
@Document("achievements")
public record Achievement(
        @Id String id,
        @Indexed(unique = true) @Schema(description = "Fester Schlüssel des Katalogs, z. B. tasks-10") String key,
        String icon,
        String name,
        String description,
        AchievementRule rule,
        @Schema(description = "Nur bei der Regel category: die Aufgaben-Kategorie")
        TaskCategory category,
        @Schema(description = "Zielwert, z. B. 10 Aufgaben oder 7 Tage")
        int target,
        @Schema(description = "Bonuspunkte beim Erreichen")
        int bonus,
        boolean active) {

    public Achievement withSettings(boolean newActive, int newTarget, int newBonus) {
        return new Achievement(id, key, icon, name, description, rule, category, newTarget, newBonus, newActive);
    }
}
