package de.familyhub.calendar;

import com.fasterxml.jackson.annotation.JsonProperty;

// Rhythmus einer Terminserie (Recurrence)
public enum Frequency {
    @JsonProperty("daily") DAILY,
    @JsonProperty("weekly") WEEKLY,
    @JsonProperty("monthly") MONTHLY,
    @JsonProperty("yearly") YEARLY
}
