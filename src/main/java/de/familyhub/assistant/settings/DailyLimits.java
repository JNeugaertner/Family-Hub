package de.familyhub.assistant.settings;

import de.familyhub.permission.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

// Wie viele Fragen eine Person am Tag stellen darf, je nach (wirksamer) Rolle (Entscheidung vom 22.10.2026); 0 heißt
// unbegrenzt. Automatische Auswertungen von WhatsApp- und Telegram-Nachrichten zählen mit.
public record DailyLimits(
        @NotNull(message = "Limit für Kinder ist Pflicht")
        @Min(value = 0, message = "Das Limit darf nicht negativ sein")
        @Max(value = MAX, message = "Höchstens " + MAX + " Fragen pro Tag")
        @Schema(description = "Kinder") Integer kind,

        @NotNull(message = "Limit für Jugendliche ist Pflicht")
        @Min(value = 0, message = "Das Limit darf nicht negativ sein")
        @Max(value = MAX, message = "Höchstens " + MAX + " Fragen pro Tag")
        @Schema(description = "Jugendliche") Integer jugendlicher,

        @NotNull(message = "Limit für Administratoren ist Pflicht")
        @Min(value = 0, message = "Das Limit darf nicht negativ sein")
        @Max(value = MAX, message = "Höchstens " + MAX + " Fragen pro Tag")
        @Schema(description = "Administratoren (Eltern)") Integer administrator) {

    static final int MAX = 1000;

    public static final DailyLimits NONE = new DailyLimits(0, 0, 0);

    // 0: unbegrenzt
    public int forRole(Role role) {
        return switch (role) {
            case KIND -> kind;
            case JUGENDLICHER -> jugendlicher;
            case ADMINISTRATOR -> administrator;
            default -> 0;
        };
    }
}
