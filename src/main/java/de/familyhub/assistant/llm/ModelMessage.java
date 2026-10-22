package de.familyhub.assistant.llm;

import java.util.List;

// Eine Nachricht im Verlauf: von der Person (User), vom Modell (Assistant, ggf. mit Werkzeug-Aufrufen) oder das
// Ergebnis eines Werkzeugs (ToolResult), das FamilyHub für einen Aufruf des Modells ausgeführt hat
public sealed interface ModelMessage {

    record User(String text) implements ModelMessage {
    }

    // text kann fehlen, wenn das Modell nur Werkzeuge aufruft
    record Assistant(String text, List<ToolCall> toolCalls) implements ModelMessage {

        public Assistant {
            toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
        }
    }

    // toolCallId gehört zu ToolCall.id; content ist das Ergebnis als Text (meist JSON)
    record ToolResult(String toolCallId, String content) implements ModelMessage {
    }
}
