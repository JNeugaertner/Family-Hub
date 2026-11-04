package de.familyhub.task;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

// Wiederholen nach Erledigung (Entscheidungen vom 04.11.2026), z. B. "Blumen gießen alle 3 Tage": Wer eine Runde
// abhakt, erzeugt sofort die nächste. Sie ist fällig am Tag des Abhakens plus Abstand und offen für alle (wer zuerst
// kommt). Wer früher abhakt, ist früher wieder dran; bleibt eine Runde liegen, stapeln sich keine neuen.
@Schema(description = "Wiederholen nach Erledigung, z. B. {\"interval\": 3, \"unit\": \"days\"}")
public record TaskRepeat(
        @NotNull(message = "Abstand ist Pflicht")
        @Min(value = 1, message = "Der Abstand muss mindestens 1 sein")
        @Max(value = 365, message = "Der Abstand darf höchstens 365 sein")
        Integer interval,

        @NotNull(message = "Einheit ist Pflicht (days, weeks oder months)")
        RepeatUnit unit) {

    // Monate: Fällt der Tag weg (31. im April), ist der letzte Tag des Monats fällig.
    public LocalDate nextDue(LocalDate doneOn) {
        return switch (unit) {
            case DAYS -> doneOn.plusDays(interval);
            case WEEKS -> doneOn.plusWeeks(interval);
            case MONTHS -> doneOn.plusMonths(interval);
        };
    }
}
