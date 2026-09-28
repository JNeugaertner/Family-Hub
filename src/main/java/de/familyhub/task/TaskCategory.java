package de.familyhub.task;

import com.fasterxml.jackson.annotation.JsonProperty;

// JSON-Werte entsprechen den Kategorien im Figma-UI (Tasks.tsx).
public enum TaskCategory {
    @JsonProperty("chores") CHORES,
    @JsonProperty("school") SCHOOL,
    @JsonProperty("health") HEALTH,
    @JsonProperty("errands") ERRANDS,
    @JsonProperty("family") FAMILY,
    @JsonProperty("home") HOME
}
