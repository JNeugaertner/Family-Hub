package de.familyhub.task;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum TaskPriority {
    @JsonProperty("low") LOW,
    @JsonProperty("medium") MEDIUM,
    @JsonProperty("high") HIGH
}
