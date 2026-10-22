package de.familyhub.assistant.llm;

import java.util.List;

// Antwort des Modells auf eine Runde. model ist das Modell, das tatsächlich geantwortet hat (laut Anbieter).
public record ModelReply(String text, List<ToolCall> toolCalls, StopReason stopReason, String model, long inputTokens,
        long outputTokens) {

    public enum StopReason {
        // fertig geantwortet
        END,
        // möchte Werkzeuge aufrufen und danach weitermachen
        TOOL_CALLS,
        // an der Längengrenze abgeschnitten
        LENGTH,
        // alles andere, z. B. vom Anbieter gefiltert
        OTHER
    }

    public ModelReply {
        toolCalls = toolCalls == null ? List.of() : List.copyOf(toolCalls);
    }
}
