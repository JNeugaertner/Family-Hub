package de.familyhub.assistant.drafts;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Component;

import de.familyhub.assistant.tools.ToolException;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import tools.jackson.core.JacksonException;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.json.JsonMapper;

// Gemeinsames für Entwürfe: Felder (wie in der API) in Einträge der Bereiche umwandeln und dabei so prüfen, wie es
// die API bei einer Anfrage täte (@Valid), sowie die Herkunft "über den Assistenten" vermerken.
@Component
public class DraftSupport {

    private static final TypeReference<Map<String, Object>> MAP = new TypeReference<>() {
    };

    // Für Meldungen, wenn ein Wert das falsche Format hat
    private static final Map<String, String> LABELS = Map.ofEntries(
            Map.entry("title", "Titel"), Map.entry("start", "Beginn"), Map.entry("end", "Ende"),
            Map.entry("memberIds", "Personen"), Map.entry("category", "Kategorie"), Map.entry("location", "Ort"),
            Map.entry("description", "Beschreibung"), Map.entry("private", "Privat"),
            Map.entry("dueDate", "Fällig am"), Map.entry("priority", "Priorität"),
            Map.entry("assigneeId", "Für wen"), Map.entry("status", "Status"), Map.entry("name", "Name"),
            Map.entry("quantity", "Menge"), Map.entry("urgent", "Dringend"), Map.entry("checked", "Gekauft"),
            Map.entry("date", "Datum"), Map.entry("type", "Mahlzeit"));

    private final JsonMapper json;
    private final Validator validator;
    private final MongoTemplate mongo;

    public DraftSupport(JsonMapper json, Validator validator, MongoTemplate mongo) {
        this.json = json;
        this.validator = validator;
        this.mongo = mongo;
    }

    // Eintrag aus den Feldern eines Entwurfs, geprüft wie eine Anfrage an die API. ToolException mit den Meldungen.
    public <T> T read(Map<String, Object> data, Class<T> type) {
        T value;
        try {
            value = json.convertValue(data, type);
        } catch (JacksonException e) {
            String field = e.getPath().isEmpty() ? null : e.getPath().getLast().getPropertyName();
            throw new ToolException(field == null ? "Ein Wert hat das falsche Format."
                    : "„" + LABELS.getOrDefault(field, field) + "“ hat das falsche Format.");
        } catch (IllegalArgumentException e) {
            throw new ToolException("Ein Wert hat das falsche Format.");
        }
        Set<ConstraintViolation<T>> violations = validator.validate(value);
        if (!violations.isEmpty()) {
            throw new ToolException(violations.stream()
                    .sorted(Comparator.comparing(v -> v.getPropertyPath().toString()))
                    .map(ConstraintViolation::getMessage)
                    .distinct()
                    .collect(Collectors.joining("; ")));
        }
        return value;
    }

    // Bestehender Eintrag mit geänderten Feldern. Leerer Text heißt "Feld leeren".
    public <T> T merge(T current, Map<String, Object> changes, Class<T> type) {
        Map<String, Object> merged = json.convertValue(current, MAP);
        changes.forEach((key, value) -> merged.put(key, value instanceof String s && s.isBlank() ? null : value));
        return read(merged, type);
    }

    // Die Felder keys eines Eintrags, z. B. für den Vergleich vorher/nachher auf der Karte. Leere Felder fehlen.
    public Map<String, Object> snapshot(Object entity, Collection<String> keys) {
        Map<String, Object> all = json.convertValue(entity, MAP);
        Map<String, Object> result = new LinkedHashMap<>();
        keys.forEach(key -> {
            if (all.get(key) != null) {
                result.put(key, all.get(key));
            }
        });
        return result;
    }

    // Zeitpunkt so, wie er in der API steht, z. B. "2026-10-23T15:00:00"
    public static String dateTime(LocalDateTime value) {
        return DateTimeFormatter.ISO_LOCAL_DATE_TIME.format(value);
    }

    // Nach dem Anlegen: Hinweis "über den Assistenten" (Entscheidung vom 22.10.2026); die Regeln des Bereichs lassen
    // ihn danach unverändert
    public void markViaAssistant(String id, Class<?> type) {
        mongo.updateFirst(Query.query(Criteria.where("id").is(id)), Update.update("viaAssistant", true), type);
    }
}
