package de.familyhub.assistant.chat;

import java.time.Instant;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;

// Eine Nachricht im Chat mit dem Assistenten. Gespeichert werden nur Frage und Antwort, nicht die Daten, die der
// Assistent dafür nachgeschlagen hat; bei Antworten auch, welche Entwürfe er dabei angelegt hat. inbound: eine
// Nachricht, die die Person FamilyHub per WhatsApp oder Telegram geschickt hat und aus der Entwürfe entstanden sind.
public record ChatTurn(
        @Schema(description = "user (Frage), assistant (Antwort) oder inbound (per WhatsApp/Telegram geschickt)")
        String role,
        String text,
        Instant at,
        @Schema(description = "Bei Antworten: Bereiche, in denen der Assistent nachgeschlagen hat, z. B. Kalender; "
                + "bei inbound: der Kanal, z. B. WhatsApp")
        List<String> sources,
        @Schema(description = "Bei Antworten: ids der Entwürfe, die der Assistent vorbereitet hat")
        List<String> draftIds) {

    public static final String USER = "user";
    public static final String ASSISTANT = "assistant";
    public static final String INBOUND = "inbound";

    public ChatTurn {
        sources = sources == null ? List.of() : List.copyOf(sources);
        draftIds = draftIds == null ? List.of() : List.copyOf(draftIds);
    }

    public ChatTurn(String role, String text, Instant at, List<String> sources) {
        this(role, text, at, sources, List.of());
    }
}
