package de.familyhub.task;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.mongodb.core.mapping.Document;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
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

        @Schema(description = "Zugewiesenes Familienmitglied; leer bei einer offenen Bonus-Aufgabe oder einer offenen "
                + "Runde einer wiederkehrenden Aufgabe")
        String assigneeId,

        @Schema(description = "Fälligkeit; bei Bonus-Aufgaben optional")
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
        String confirmedBy,

        @Schema(description = "Bonus-Aufgabe: offen für alle Kinder und Jugendlichen, wer sie übernimmt, bekommt die Punkte")
        Boolean bonus,

        @Schema(description = "Nur bei Bonus-Aufgaben: nach der Bestätigung automatisch wieder offen")
        Boolean repeatable,

        @Valid
        @Schema(description = "Wiederholen nach Erledigung (nicht bei Bonus-Aufgaben): Beim Abhaken entsteht die nächste "
                + "Runde, fällig am Tag des Abhakens plus Abstand und offen für alle. Anlegen und ändern nur mit "
                + "\"aufgaben/erstellen/familie\".")
        TaskRepeat repeat,

        @Schema(accessMode = Schema.AccessMode.READ_ONLY,
                description = "Bei wiederkehrenden Aufgaben: wer die Runde davor wann abgehakt hat")
        PreviousRound previousRound) {

    @PersistenceCreator
    public Task {
        points = points == null ? 0 : points;
        status = status == null ? TaskStatus.TODO : status;
        bonus = Boolean.TRUE.equals(bonus);
        repeatable = bonus && Boolean.TRUE.equals(repeatable);
        assigneeId = assigneeId == null || assigneeId.isBlank() ? null : assigneeId;
        repeat = bonus ? null : repeat;
        previousRound = repeat == null ? null : previousRound;
    }

    public Task(String id, String title, String description, String assigneeId, LocalDate dueDate,
            TaskPriority priority, TaskCategory category, Integer points, TaskStatus status, String createdBy,
            LocalDateTime confirmedAt, String confirmedBy, Boolean bonus, Boolean repeatable) {
        this(id, title, description, assigneeId, dueDate, priority, category, points, status, createdBy, confirmedAt,
                confirmedBy, bonus, repeatable, null, null);
    }

    public Task(String id, String title, String description, String assigneeId, LocalDate dueDate,
            TaskPriority priority, TaskCategory category, Integer points, TaskStatus status, String createdBy,
            LocalDateTime confirmedAt, String confirmedBy) {
        this(id, title, description, assigneeId, dueDate, priority, category, points, status, createdBy, confirmedAt,
                confirmedBy, false, false);
    }

    public Task(String id, String title, String description, String assigneeId, LocalDate dueDate,
            TaskPriority priority, TaskCategory category, int points) {
        this(id, title, description, assigneeId, dueDate, priority, category, points, TaskStatus.TODO, null, null,
                null);
    }

    // Bonus-Aufgabe, die noch niemand übernommen hat
    @JsonIgnore
    public boolean isOpenBonus() {
        return bonus && assigneeId == null;
    }

    // Runde einer wiederkehrenden Aufgabe, die noch niemand übernommen hat (wer zuerst kommt)
    @JsonIgnore
    public boolean isOpenRound() {
        return repeat != null && assigneeId == null;
    }

    // Wartet auf die Bestätigung durch einen Administrator (erledigt und mit Punkten)
    @JsonIgnore
    public boolean isAwaitingConfirmation() {
        return status == TaskStatus.DONE && points > 0;
    }

    // Abgeschlossen: bestätigt, oder erledigt ohne Punkte (nichts wartet mehr auf eine Bestätigung)
    @JsonIgnore
    public boolean isCompleted() {
        return status == TaskStatus.CONFIRMED || (status == TaskStatus.DONE && points == 0);
    }

    public Task withStatus(TaskStatus newStatus) {
        return new Task(id, title, description, assigneeId, dueDate, priority, category, points, newStatus, createdBy,
                confirmedAt, confirmedBy, bonus, repeatable, repeat, previousRound);
    }

    // Aufgabe ohne Punkte (Erwachsene sammeln keine Sterne)
    public Task withoutPoints() {
        return withPoints(0);
    }

    public Task withPoints(int newPoints) {
        return new Task(id, title, description, assigneeId, dueDate, priority, category, newPoints, status, createdBy,
                confirmedAt, confirmedBy, bonus, repeatable, repeat, previousRound);
    }

    public Task withAssignee(String newAssigneeId, TaskStatus newStatus) {
        return new Task(id, title, description, newAssigneeId, dueDate, priority, category, points, newStatus,
                createdBy, confirmedAt, confirmedBy, bonus, repeatable, repeat, previousRound);
    }

    public Task confirmed(LocalDateTime at, String by) {
        return new Task(id, title, description, assigneeId, dueDate, priority, category, points, TaskStatus.CONFIRMED,
                createdBy, at, by, bonus, repeatable, repeat, previousRound);
    }

    // Wiederkehrende Bonus-Aufgabe: nach der Bestätigung eine neue, offene Aufgabe ohne Frist
    public Task nextRound() {
        return new Task(null, title, description, null, null, priority, category, points, TaskStatus.TODO, createdBy,
                null, null, true, true);
    }

    // Wiederkehrende Aufgabe: die nächste Runde nach dem Abhaken, offen für alle
    public Task followingRound(LocalDate doneOn, String doneBy) {
        return new Task(null, title, description, null, repeat.nextDue(doneOn), priority, category, points,
                TaskStatus.TODO, createdBy, null, null, false, false, repeat, new PreviousRound(id, doneOn, doneBy));
    }
}
