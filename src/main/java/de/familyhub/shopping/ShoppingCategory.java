package de.familyhub.shopping;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum ShoppingCategory {
    @JsonProperty("milchprodukte") MILCHPRODUKTE,
    @JsonProperty("backwaren") BACKWAREN,
    @JsonProperty("fleisch") FLEISCH,
    @JsonProperty("gemuese") GEMUESE,
    @JsonProperty("obst") OBST,
    @JsonProperty("getraenke") GETRAENKE,
    @JsonProperty("vorrat") VORRAT,
    @JsonProperty("tiefkuehl") TIEFKUEHL,
    @JsonProperty("snacks") SNACKS,
    @JsonProperty("haushalt") HAUSHALT,
    @JsonProperty("zutaten_essensplanung") ZUTATEN_ESSENSPLANUNG
}
