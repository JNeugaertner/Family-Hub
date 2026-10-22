package de.familyhub.assistant.chat;

import java.time.Instant;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

// Eine Nachricht im Chat mit dem Assistenten. Gespeichert werden nur Frage und Antwort, nicht die Daten, die der
// Assistent dafür nachgeschlagen hat.
public record ChatTurn(
        @Schema(description = "user (Frage) oder assistant (Antwort)") String role,
        String text,
        Instant at,
        @Schema(description = "Bei Antworten: Bereiche, in denen der Assistent nachgeschlagen hat, z. B. Kalender")
        List<String> sources) {

    public static final String USER = "user";
    public static final String ASSISTANT = "assistant";

    public ChatTurn {
        sources = sources == null ? List.of() : List.copyOf(sources);
    }
}
