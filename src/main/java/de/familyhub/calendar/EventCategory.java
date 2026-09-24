package de.familyhub.calendar;

import com.fasterxml.jackson.annotation.JsonProperty;

// JSON-Werte entsprechen den Kategorien im Figma-UI (data.ts).
public enum EventCategory {
    @JsonProperty("school") SCHOOL,
    @JsonProperty("sports") SPORTS,
    @JsonProperty("appointment") APPOINTMENT,
    @JsonProperty("family") FAMILY,
    @JsonProperty("work") WORK,
    @JsonProperty("reminder") REMINDER
}
