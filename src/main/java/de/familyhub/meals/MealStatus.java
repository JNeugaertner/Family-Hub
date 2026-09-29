package de.familyhub.meals;

import com.fasterxml.jackson.annotation.JsonProperty;

// approved: steht im Plan; proposed: Wunsch eines Kindes, wartet auf die Eltern
public enum MealStatus {
    @JsonProperty("approved") APPROVED,
    @JsonProperty("proposed") PROPOSED
}
