package de.familyhub.assistant.settings;

import java.util.EnumSet;
import java.util.Set;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.mongodb.core.mapping.Document;

import com.fasterxml.jackson.annotation.JsonIgnore;

import de.familyhub.assistant.AssistantArea;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

// Einstellungen des KI-Assistenten für die ganze Familie (Admin-Karte, Entscheidungen vom 22.10.2026), als ein
// einziges Dokument gespeichert. Ohne gespeicherte Einstellungen ist er eingeschaltet, darf alle Bereiche nutzen, hat
// kein Tageslimit und wertet den Eingang nicht aus.
@Document("assistantSettings")
public record AssistantSettings(
        @Id @JsonIgnore String id,

        @NotNull(message = "enabled ist Pflicht")
        @Schema(description = "false: Der Assistent ist für alle abgeschaltet; die Hinweise ohne KI bleiben")
        Boolean enabled,

        @NotNull(message = "areas ist Pflicht")
        @Schema(description = "Bereiche, auf die der Assistent zugreifen darf")
        Set<AssistantArea> areas,

        @NotNull(message = "inboxDrafts ist Pflicht")
        @Schema(description = "WhatsApp- und Telegram-Nachrichten von Familienmitgliedern automatisch zu Entwürfen "
                + "auswerten (nur, wenn der Assistent eingeschaltet ist)")
        Boolean inboxDrafts,

        @NotNull(message = "dailyLimits ist Pflicht")
        @Valid
        @Schema(description = "Fragen pro Tag und Person je Rolle; 0 = unbegrenzt")
        DailyLimits dailyLimits) {

    public static final String ID = "assistant";

    @PersistenceCreator
    public AssistantSettings {
        areas = areas == null ? null : Set.copyOf(areas);
    }

    public static AssistantSettings defaults() {
        return new AssistantSettings(ID, true, EnumSet.allOf(AssistantArea.class), false, DailyLimits.NONE);
    }

    AssistantSettings withId() {
        return new AssistantSettings(ID, enabled, areas, inboxDrafts, dailyLimits);
    }

    @JsonIgnore
    public boolean allows(AssistantArea area) {
        return areas.contains(area);
    }
}
