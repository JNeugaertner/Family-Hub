package de.familyhub.assistant.drafts;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;

// Ein Eintrag eines Entwurfs: etwas anlegen, ändern oder löschen. Die Felder heißen wie in der API des Bereichs
// (z. B. title, start, memberIds bei Terminen), damit die Oberfläche sie wie dort anzeigen und bearbeiten kann.
public record DraftItem(
        Action action,

        @Schema(description = "Bei update und delete: der betroffene Eintrag")
        String targetId,

        @Schema(description = "create: der neue Eintrag; update: nur die geänderten Felder; delete: leer")
        Map<String, Object> data,

        @Schema(description = "Bei update und delete: der Eintrag, wie er beim Entwerfen war (zum Vergleichen)")
        Map<String, Object> before,

        State state,

        @Schema(description = "Nach dem Übernehmen: approved, oder proposed, wenn daraus ein Vorschlag wurde")
        String outcome,

        @Schema(description = "Warum das letzte Übernehmen nicht ging; der Eintrag bleibt dann offen")
        String error) {

    public enum Action {
        @JsonProperty("create") CREATE,
        @JsonProperty("update") UPDATE,
        @JsonProperty("delete") DELETE
    }

    public enum State {
        @JsonProperty("open") OPEN,
        @JsonProperty("applied") APPLIED,
        @JsonProperty("discarded") DISCARDED
    }

    public DraftItem {
        data = data == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(data));
        before = before == null ? Map.of() : Collections.unmodifiableMap(new LinkedHashMap<>(before));
        state = state == null ? State.OPEN : state;
    }

    static DraftItem create(Map<String, Object> data) {
        return new DraftItem(Action.CREATE, null, data, null, State.OPEN, null, null);
    }

    static DraftItem update(String targetId, Map<String, Object> changes, Map<String, Object> before) {
        return new DraftItem(Action.UPDATE, targetId, changes, before, State.OPEN, null, null);
    }

    static DraftItem delete(String targetId, Map<String, Object> before) {
        return new DraftItem(Action.DELETE, targetId, null, before, State.OPEN, null, null);
    }

    DraftItem applied(Map<String, Object> finalData, String result) {
        return new DraftItem(action, targetId, finalData, before, State.APPLIED, result, null);
    }

    DraftItem failed(Map<String, Object> triedData, String message) {
        return new DraftItem(action, targetId, triedData, before, State.OPEN, null, message);
    }

    DraftItem discarded() {
        return new DraftItem(action, targetId, data, before, State.DISCARDED, null, null);
    }
}
