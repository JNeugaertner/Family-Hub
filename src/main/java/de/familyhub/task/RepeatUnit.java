package de.familyhub.task;

import com.fasterxml.jackson.annotation.JsonProperty;

// Einheit des Abstands einer wiederkehrenden Aufgabe (TaskRepeat)
public enum RepeatUnit {
    @JsonProperty("days") DAYS,
    @JsonProperty("weeks") WEEKS,
    @JsonProperty("months") MONTHS
}
