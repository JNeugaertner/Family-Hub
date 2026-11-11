package de.familyhub.undo;

import java.time.Instant;
import java.util.List;

import org.bson.Document;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;

// Stand vor einer Löschung, damit „Rückgängig“ ihn wiederherstellen kann (Entscheidung vom 11.11.2026): die
// gelöschten oder geänderten Dokumente so, wie sie in der Datenbank standen. MongoDB entfernt den Eintrag nach
// 15 Minuten von selbst; wiederherstellen lässt er sich nur kürzer (UndoService.WINDOW) und nur von derselben Person.
@org.springframework.data.mongodb.core.mapping.Document("undoSnapshots")
public record UndoSnapshot(
        @Id String id,
        // Bereich wie im Pfad: events, tasks oder shopping
        String area,
        String memberId,
        @Indexed(expireAfter = "15m") Instant createdAt,
        List<Document> documents) {
}
