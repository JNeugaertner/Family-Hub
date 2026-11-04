package de.familyhub.task;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

// Wiederholen nach Erledigung (Entscheidungen vom 04.11.2026), z. B. "Blumen gießen alle 3 Tage": Wer eine Runde
// abhakt, erzeugt sofort die nächste, fällig am Tag des Abhakens plus Abstand. Wer zuerst kommt (openToAll): Die
// nächste Runde ist für alle offen. Feste Person: Sie geht wieder an dieselbe Person. Wer früher abhakt, ist früher
// wieder dran; bleibt eine Runde liegen, stapeln sich keine neuen.
@Schema(description = "Wiederholen nach Erledigung, z. B. {\"interval\": 3, \"unit\": \"days\", \"openToAll\": true}")
public record TaskRepeat(
        @NotNull(message = "Abstand ist Pflicht")
        @Min(value = 1, message = "Der Abstand muss mindestens 1 sein")
        @Max(value = 365, message = "Der Abstand darf höchstens 365 sein")
        Integer interval,

        @NotNull(message = "Einheit ist Pflicht (days, weeks oder months)")
        RepeatUnit unit,

        @Schema(description = "true: jede neue Runde ist für alle offen (wer zuerst kommt); false: sie geht wieder an "
                + "dieselbe Person. Fehlt der Wert, gilt beim Anlegen: ohne Person für alle offen, mit Person fest.")
        Boolean openToAll) {

    // Runden aus der Zeit vor der festen Person waren immer für alle offen. Bewusst kein "isOpenToAll": Jackson hielte
    // das für einen Getter von openToAll, und ein @JsonIgnore daran würde openToAll beim Einlesen verschlucken.
    public boolean nextRoundOpenToAll() {
        return !Boolean.FALSE.equals(openToAll);
    }

    public TaskRepeat withOpenToAll(boolean value) {
        return new TaskRepeat(interval, unit, value);
    }

    // Monate: Fällt der Tag weg (31. im April), ist der letzte Tag des Monats fällig.
    public LocalDate nextDue(LocalDate doneOn) {
        return switch (unit) {
            case DAYS -> doneOn.plusDays(interval);
            case WEEKS -> doneOn.plusWeeks(interval);
            case MONTHS -> doneOn.plusMonths(interval);
        };
    }
}
