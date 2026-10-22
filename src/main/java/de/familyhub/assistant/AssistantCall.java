package de.familyhub.assistant;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

// Ein Aufruf des Sprachmodells, für Kosten und Fehlersuche (Entscheidung vom 22.10.2026). Bewusst ohne Inhalte:
// keine Fragen, Antworten oder Familiendaten. MongoDB löscht jeden Eintrag nach 30 Tagen von selbst.
// purpose: wofür aufgerufen wurde, z. B. "verbindungstest"; error: Meldung für Menschen, wenn ok false ist
@Document("assistantCalls")
public record AssistantCall(
        @Id String id,
        @Indexed(expireAfter = "30d") Instant at,
        @Indexed String memberId,
        String purpose,
        String provider,
        String model,
        boolean ok,
        String error,
        long inputTokens,
        long outputTokens,
        int toolCalls,
        long durationMillis) {
}
