package de.familyhub.shopping;

import com.fasterxml.jackson.annotation.JsonProperty;

// approved: steht auf der Liste; proposed: Vorschlag eines Kindes, wartet auf die Eltern
public enum ShoppingItemStatus {
    @JsonProperty("approved") APPROVED,
    @JsonProperty("proposed") PROPOSED
}
