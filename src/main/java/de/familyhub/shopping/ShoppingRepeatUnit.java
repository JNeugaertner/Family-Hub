package de.familyhub.shopping;

import com.fasterxml.jackson.annotation.JsonProperty;

// Wie oft ein wiederkehrender Artikel wiederkommt (ShoppingRepeat): bei jedem Einkauf oder nach einem Abstand
public enum ShoppingRepeatUnit {
    @JsonProperty("trip") TRIP,
    @JsonProperty("days") DAYS,
    @JsonProperty("weeks") WEEKS,
    @JsonProperty("months") MONTHS
}
