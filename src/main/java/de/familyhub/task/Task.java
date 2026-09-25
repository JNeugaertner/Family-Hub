package de.familyhub.task;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.mongodb.core.mapping.Document;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Document("tasks")
public record Task(
        @Id @Schema(accessMode = Schema.AccessMode.READ_ONLY) String id,

        @NotBlank(message = "Titel darf nicht leer sein")
        @Size(max = 100, message = "Titel darf höchstens 100 Zeichen lang sein")
        String title,

        @Size(max = 1000, message = "Beschreibung darf höchstens 1000 Zeichen lang sein")
        String description,

        @NotBlank(message = "Aufgabe muss einem Familienmitglied zugewiesen sein")
        String assigneeId,

        @NotNull(message = "Fälligkeit ist Pflicht")
        LocalDate dueDate,

        @NotNull(message = "Priorität ist Pflicht")
        TaskPriority priority,

        @NotNull(message = "Kategorie ist Pflicht")
        TaskCategory category,

        @Min(value = 0, message = "Punkte dürfen nicht negativ sein")
        @Max(value = 1000, message = "Höchstens 1000 Punkte je Aufgabe")
        @Schema(description = "Punkte, die nach bestätigter Erledigung gutgeschrieben werden (nur Administratoren)")
        Integer points,

        @Schema(accessMode = Schema.AccessMode.READ_ONLY,
                description = "todo, inprogress, done (erledigt) oder confirmed (bestätigt, Punkte gutgeschrieben); "
                        + "ändern über PATCH /api/tasks/{id}/status")
        TaskStatus status,

        @Schema(accessMode = Schema.AccessMode.READ_ONLY, description = "Id des Mitglieds, das die Aufgabe angelegt hat")
        String createdBy,

        @Schema(accessMode = Schema.AccessMode.READ_ONLY)
        LocalDateTime confirmedAt,

        @Schema(accessMode = Schema.AccessMode.READ_ONLY, description = "Id des Administrators, der bestätigt hat")
        String confirmedBy) {

    @PersistenceCreator
    public Task {
        points = points == null ? 0 : points;
        status = status == null ? TaskStatus.TODO : status;
    }

    public Task(String id, String title, String description, String assigneeId, LocalDate dueDate,
            TaskPriority priority, TaskCategory category, int points) {
        this(id, title, description, assigneeId, dueDate, priority, category, points, TaskStatus.TODO, null, null,
                null);
    }

    // Wartet auf die Bestätigung durch einen Administrator (erledigt und mit Punkten)
    @JsonIgnore
    public boolean isAwaitingConfirmation() {
        return status == TaskStatus.DONE && points > 0;
    }

    public Task withStatus(TaskStatus newStatus) {
        return new Task(id, title, description, assigneeId, dueDate, priority, category, points, newStatus, createdBy,
                confirmedAt, confirmedBy);
    }
}
