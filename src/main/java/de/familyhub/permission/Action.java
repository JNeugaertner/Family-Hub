package de.familyhub.permission;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

public enum Action {
    ANSEHEN, ERSTELLEN, BEARBEITEN, LOESCHEN, VORSCHLAGEN, FREIGEBEN, VERWALTEN;

    @JsonValue
    public String json() {
        return name().toLowerCase(Locale.ROOT);
    }

    @JsonCreator
    public static Action fromJson(String value) {
        return valueOf(value.toUpperCase(Locale.ROOT));
    }
}
