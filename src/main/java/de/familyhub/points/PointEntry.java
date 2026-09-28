package de.familyhub.points;

import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import io.swagger.v3.oas.annotations.media.Schema;

// Eine Punkte-Buchung. Der Punktestand ist die Summe aller Buchungen eines Mitglieds, die Buchungen selbst
// sind die Historie. Eine Aufgabe kann höchstens einmal Punkte bringen (eindeutiger Index auf taskId).
@Document("points")
public record PointEntry(
        @Id String id,
        String memberId,
        int amount,
        String reason,
        @Indexed(unique = true, sparse = true)
        @Schema(description = "Aufgabe, für die die Punkte gutgeschrieben wurden (leer bei anderen Buchungen)")
        String taskId,
        LocalDateTime createdAt,
        @Schema(description = "Id des Mitglieds, das die Punkte vergeben hat")
        String createdBy) {
}
