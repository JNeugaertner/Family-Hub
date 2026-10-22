package de.familyhub.assistant;

import com.fasterxml.jackson.annotation.JsonProperty;

// Bereiche, auf die der Assistent zugreift; Administratoren können sie einzeln abschalten (Entscheidung vom
// 22.10.2026). Jedes Werkzeug gehört zu genau einem Bereich.
public enum AssistantArea {
    @JsonProperty("kalender") KALENDER,
    @JsonProperty("aufgaben") AUFGABEN,
    @JsonProperty("einkauf") EINKAUF,
    @JsonProperty("essen") ESSEN,
    @JsonProperty("wetter_muell") WETTER_MUELL,
    @JsonProperty("nachrichten") NACHRICHTEN
}
