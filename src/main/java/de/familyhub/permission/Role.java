package de.familyhub.permission;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

// Rollen laut Rollenkonzept. JSON-Werte in Kleinbuchstaben, passend zum Frontend (src/roles).
public enum Role {
    ADMINISTRATOR("Administrator"),
    JUGENDLICHER("Jugendlicher"),
    KIND("Kind"),
    GAST("Gast"),
    // Systemakteur für den späteren KI-Service; kann keinem Familienmitglied zugewiesen werden.
    KI_AGENT("KI-Agent");

    private final String displayName;

    Role(String displayName) {
        this.displayName = displayName;
    }

    public String displayName() {
        return displayName;
    }

    // Für die Familiengrenze aus User Story A.1 zählen Jugendliche als Kinder.
    public boolean countsAsChild() {
        return this == KIND || this == JUGENDLICHER;
    }

    @JsonValue
    public String json() {
        return name().toLowerCase(Locale.ROOT);
    }

    @JsonCreator
    public static Role fromJson(String value) {
        return valueOf(value.toUpperCase(Locale.ROOT));
    }
}
