package de.familyhub.calendar;

import com.fasterxml.jackson.annotation.JsonProperty;

// Freigabe-Workflow: Vorschläge (z. B. von Jugendlichen für andere) gelten erst nach Freigabe durch einen
// Administrator; abgelehnte Vorschläge werden verworfen.
public enum EventStatus {
    @JsonProperty("approved") APPROVED,
    @JsonProperty("proposed") PROPOSED
}
