package de.familyhub.assistant.llm;

import java.util.Map;

// Ein Werkzeug, das das Modell aufrufen darf. parameters ist ein JSON-Schema (als verschachtelte Map), das die
// Argumente beschreibt. Was ein Werkzeug tut, entscheidet allein FamilyHub; das Modell kann es nur anfragen.
public record ToolSpec(String name, String description, Map<String, Object> parameters) {

    public ToolSpec {
        parameters = Map.copyOf(parameters);
    }
}
