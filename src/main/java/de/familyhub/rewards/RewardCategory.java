package de.familyhub.rewards;

import com.fasterxml.jackson.annotation.JsonProperty;

public enum RewardCategory {
    @JsonProperty("essen") ESSEN,
    @JsonProperty("freizeit") FREIZEIT,
    @JsonProperty("ausflug") AUSFLUG,
    @JsonProperty("geschenk") GESCHENK
}
