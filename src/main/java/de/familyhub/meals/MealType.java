package de.familyhub.meals;

import com.fasterxml.jackson.annotation.JsonProperty;

// Mahlzeiten des Wochenplans, in der Reihenfolge des Tages
public enum MealType {
    @JsonProperty("fruehstueck") FRUEHSTUECK,
    @JsonProperty("mittagessen") MITTAGESSEN,
    @JsonProperty("abendessen") ABENDESSEN,
    @JsonProperty("snacks") SNACKS
}
