package de.familyhub.shopping;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

// Wiederkehrende Artikel (Entscheidung vom 05.11.2026), je Artikel wählbar:
// - trip, bei jedem Einkauf: "Abgehakte entfernen" löscht den Artikel nicht, er steht sofort wieder offen da.
// - days/weeks/months: Der Artikel kommt den Abstand nach dem Kauf (Tag des Abhakens) offen zurück. Bis dahin steht er
//   unter "Kommt wieder", sobald jemand die abgehakten Artikel entfernt hat (ShoppingItem.returnsOn).
@Schema(description = "Wiederkehrender Artikel: {\"unit\": \"trip\"} bei jedem Einkauf, "
        + "{\"interval\": 2, \"unit\": \"weeks\"} zwei Wochen nach dem Kauf")
public record ShoppingRepeat(
        @Min(value = 1, message = "Der Abstand muss mindestens 1 sein")
        @Max(value = 365, message = "Der Abstand darf höchstens 365 sein")
        @Schema(description = "Abstand ab dem Kauf; Pflicht außer bei unit trip")
        Integer interval,

        @NotNull(message = "Wie oft der Artikel wiederkommt, ist Pflicht (trip, days, weeks oder months)")
        ShoppingRepeatUnit unit) {

    public ShoppingRepeat {
        interval = unit == ShoppingRepeatUnit.TRIP ? null : interval;
    }

    public boolean everyTrip() {
        return unit == ShoppingRepeatUnit.TRIP;
    }

    // Monate: Fällt der Tag weg (31. im April), kommt der Artikel am letzten Tag des Monats wieder.
    public LocalDate nextDue(LocalDate boughtOn) {
        return switch (unit) {
            case TRIP -> boughtOn;
            case DAYS -> boughtOn.plusDays(interval);
            case WEEKS -> boughtOn.plusWeeks(interval);
            case MONTHS -> boughtOn.plusMonths(interval);
        };
    }
}
