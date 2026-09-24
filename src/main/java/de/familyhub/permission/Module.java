package de.familyhub.permission;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum Module {
    FAMILIE, KALENDER, AUFGABEN, PUNKTE, EINKAUF, ESSEN, WETTER, MUELL, FAHRZEIT, MESSENGER, SPRACHASSISTENT, SYSTEM;

    @JsonValue
    public String json() {
        return name().toLowerCase(Locale.ROOT);
    }

    @JsonCreator
    public static Module fromJson(String value) {
        return valueOf(value.toUpperCase(Locale.ROOT));
    }
}
