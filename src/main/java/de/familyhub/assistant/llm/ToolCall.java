package de.familyhub.assistant.llm;

// Das Modell möchte ein Werkzeug aufrufen. argumentsJson ist so, wie das Modell es geliefert hat; FamilyHub muss es
// vor dem Ausführen prüfen, weil ein Modell auch ungültiges JSON oder unpassende Werte schicken kann.
public record ToolCall(String id, String name, String argumentsJson) {
}
