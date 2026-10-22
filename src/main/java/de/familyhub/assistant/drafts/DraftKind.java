package de.familyhub.assistant.drafts;

import com.fasterxml.jackson.annotation.JsonProperty;

// Wofür ein Entwurf ist. area ist der Bereich der API, dessen Seiten nach dem Übernehmen neu laden (Live-Updates).
public enum DraftKind {
    @JsonProperty("event") EVENT("events"),
    @JsonProperty("task") TASK("tasks"),
    @JsonProperty("shopping") SHOPPING("shopping"),
    @JsonProperty("meal") MEAL("meals"),
    @JsonProperty("message") MESSAGE("messages");

    private final String area;

    DraftKind(String area) {
        this.area = area;
    }

    public String area() {
        return area;
    }
}
