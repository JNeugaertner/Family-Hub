package de.familyhub.assistant.tools;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.Optional;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

// Die Argumente eines Werkzeug-Aufrufs, so wie das Modell sie geschickt hat. Ein Modell kann ungültiges JSON oder
// unpassende Werte liefern; dann gibt es eine ToolException mit einer Meldung, aus der es lernen kann.
public final class ToolArguments {

    private static final ObjectMapper JSON = new ObjectMapper();

    private final JsonNode node;

    private ToolArguments(JsonNode node) {
        this.node = node;
    }

    public static ToolArguments parse(String json) {
        if (json == null || json.isBlank()) {
            return new ToolArguments(JSON.createObjectNode());
        }
        try {
            JsonNode node = JSON.readTree(json);
            if (!node.isObject()) {
                throw new ToolException("Die Argumente müssen ein JSON-Objekt sein.");
            }
            return new ToolArguments(node);
        } catch (JacksonException e) {
            throw new ToolException("Die Argumente sind kein gültiges JSON.");
        }
    }

    public Optional<String> text(String name) {
        JsonNode value = node.path(name);
        if (value.isMissingNode() || value.isNull()) {
            return Optional.empty();
        }
        if (!value.isString()) {
            throw new ToolException(name + " muss ein Text sein.");
        }
        String text = value.stringValue().strip();
        return text.isEmpty() ? Optional.empty() : Optional.of(text);
    }

    // Datum im Format JJJJ-MM-TT
    public Optional<LocalDate> date(String name) {
        return text(name).map(value -> {
            try {
                return LocalDate.parse(value);
            } catch (DateTimeParseException e) {
                throw new ToolException(name + " muss ein Datum im Format JJJJ-MM-TT sein, nicht „" + value + "“.");
            }
        });
    }

    public boolean flag(String name, boolean fallback) {
        JsonNode value = node.path(name);
        if (value.isMissingNode() || value.isNull()) {
            return fallback;
        }
        if (!value.isBoolean()) {
            throw new ToolException(name + " muss true oder false sein.");
        }
        return value.booleanValue();
    }
}
