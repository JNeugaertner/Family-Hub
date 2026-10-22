package de.familyhub.assistant.tools;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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

    // Datum mit Uhrzeit im Format JJJJ-MM-TTTHH:MM
    public Optional<LocalDateTime> dateTime(String name) {
        return text(name).map(value -> {
            try {
                return LocalDateTime.parse(value);
            } catch (DateTimeParseException e) {
                throw new ToolException(name + " muss Datum und Uhrzeit im Format JJJJ-MM-TTTHH:MM sein, nicht „"
                        + value + "“.");
            }
        });
    }

    // Ein Wert aus einer festen Auswahl
    public Optional<String> choice(String name, Collection<String> allowed) {
        return text(name).map(value -> {
            if (!allowed.contains(value)) {
                throw new ToolException(name + " muss einer dieser Werte sein: " + String.join(", ", allowed) + ".");
            }
            return value;
        });
    }

    public boolean flag(String name, boolean fallback) {
        return optionalFlag(name).orElse(fallback);
    }

    public Optional<Boolean> optionalFlag(String name) {
        JsonNode value = node.path(name);
        if (value.isMissingNode() || value.isNull()) {
            return Optional.empty();
        }
        if (!value.isBoolean()) {
            throw new ToolException(name + " muss true oder false sein.");
        }
        return Optional.of(value.booleanValue());
    }

    // Liste von Texten, z. B. Namen; leer, wenn die Angabe fehlt
    public Optional<List<String>> texts(String name) {
        JsonNode value = node.path(name);
        if (value.isMissingNode() || value.isNull()) {
            return Optional.empty();
        }
        if (!value.isArray()) {
            throw new ToolException(name + " muss eine Liste von Texten sein.");
        }
        List<String> result = new ArrayList<>();
        for (JsonNode element : value) {
            if (!element.isString() || element.stringValue().isBlank()) {
                throw new ToolException(name + " muss eine Liste von Texten sein.");
            }
            result.add(element.stringValue().strip());
        }
        return Optional.of(result);
    }

    // Liste von Objekten, z. B. die Einträge eines Entwurfs
    public List<ToolArguments> objects(String name) {
        JsonNode value = node.path(name);
        if (value.isMissingNode() || value.isNull()) {
            return List.of();
        }
        if (!value.isArray()) {
            throw new ToolException(name + " muss eine Liste sein.");
        }
        List<ToolArguments> result = new ArrayList<>();
        for (JsonNode element : value) {
            if (!element.isObject()) {
                throw new ToolException("Jeder Eintrag in " + name + " muss ein Objekt sein.");
            }
            result.add(new ToolArguments(element));
        }
        return result;
    }

    public boolean has(String name) {
        JsonNode value = node.path(name);
        return !value.isMissingNode() && !value.isNull();
    }

    // Angaben, die das Werkzeug nicht kennt, zurückweisen, statt sie still zu übergehen
    public void requireOnly(Set<String> known) {
        for (String name : node.propertyNames()) {
            if (!known.contains(name)) {
                throw new ToolException("Unbekannte Angabe „" + name + "“. Möglich sind: "
                        + String.join(", ", known.stream().sorted().toList()) + ".");
            }
        }
    }
}
