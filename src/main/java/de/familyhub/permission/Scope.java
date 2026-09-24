package de.familyhub.permission;

import java.util.Locale;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonValue;

// Geltungsbereich, von eng nach weit. Ein weiterer Bereich deckt die engeren mit ab.
public enum Scope {
    // nur eigene Daten
    EIGEN,
    // Daten, die ein Administrator freigegeben hat (z. B. Kategorien für Gäste)
    FREIGEGEBEN,
    // alle Daten der Familie
    FAMILIE;

    public boolean covers(Scope requested) {
        return ordinal() >= requested.ordinal();
    }

    @JsonValue
    public String json() {
        return name().toLowerCase(Locale.ROOT);
    }

    @JsonCreator
    public static Scope fromJson(String value) {
        return valueOf(value.toUpperCase(Locale.ROOT));
    }
}
