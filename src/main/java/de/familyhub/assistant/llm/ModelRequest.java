package de.familyhub.assistant.llm;

import java.util.List;

// Eine Anfrage an das Sprachmodell: Anweisungen für das Modell (system), der bisherige Verlauf und die Werkzeuge,
// die es aufrufen darf
public record ModelRequest(String system, List<ModelMessage> messages, List<ToolSpec> tools) {

    public ModelRequest {
        messages = List.copyOf(messages);
        tools = tools == null ? List.of() : List.copyOf(tools);
    }
}
