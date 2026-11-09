package de.familyhub.shopping;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.mongodb.core.mapping.Document;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Document("shoppingItems")
public record ShoppingItem(
        @Id @Schema(accessMode = Schema.AccessMode.READ_ONLY) String id,

        @NotBlank(message = "Artikel darf nicht leer sein")
        @Size(max = 80, message = "Artikel darf höchstens 80 Zeichen lang sein")
        String name,

        @Size(max = 30, message = "Menge darf höchstens 30 Zeichen lang sein")
        @Schema(description = "Freitext, z. B. 2 × 1 l")
        String quantity,

        @NotNull(message = "Kategorie ist Pflicht")
        ShoppingCategory category,

        @Schema(description = "Dringend: steht oben auf der Liste")
        Boolean urgent,

        @Schema(accessMode = Schema.AccessMode.READ_ONLY, description = "Gekauft (abgehakt); ändern über PATCH .../checked")
        Boolean checked,

        @Schema(accessMode = Schema.AccessMode.READ_ONLY,
                description = "approved oder proposed (Vorschlag, wartet auf einen Administrator)")
        ShoppingItemStatus status,

        @Schema(accessMode = Schema.AccessMode.READ_ONLY) String createdBy,
        @Schema(accessMode = Schema.AccessMode.READ_ONLY) LocalDateTime createdAt,
        @Schema(accessMode = Schema.AccessMode.READ_ONLY) String checkedBy,

        @Valid
        @Schema(description = "Wiederkehrender Artikel; fehlt: einmalig, \"Abgehakte entfernen\" löscht ihn")
        ShoppingRepeat repeat,

        @Schema(accessMode = Schema.AccessMode.READ_ONLY, description = "Tag des Abhakens (gekauft)")
        LocalDate checkedOn,

        @Schema(accessMode = Schema.AccessMode.READ_ONLY,
                description = "Gekauft und vorerst von der Liste genommen: kommt an diesem Tag offen wieder")
        LocalDate returnsOn) {

    @PersistenceCreator
    public ShoppingItem {
        urgent = Boolean.TRUE.equals(urgent);
        checked = Boolean.TRUE.equals(checked);
        status = status == null ? ShoppingItemStatus.APPROVED : status;
        checkedOn = checked ? checkedOn : null;
        // Warten gibt es nur bei gekauften Artikeln mit Abstand
        returnsOn = checked && repeat != null && !repeat.everyTrip() ? returnsOn : null;
    }

    public ShoppingItem(String id, String name, String quantity, ShoppingCategory category, Boolean urgent,
            Boolean checked, ShoppingItemStatus status, String createdBy, LocalDateTime createdAt, String checkedBy) {
        this(id, name, quantity, category, urgent, checked, status, createdBy, createdAt, checkedBy, null, null, null);
    }

    @JsonIgnore
    public boolean isProposal() {
        return status == ShoppingItemStatus.PROPOSED;
    }

    // Gekauft, steht unter "Kommt wieder" statt auf der Liste
    @JsonIgnore
    public boolean isWaiting() {
        return returnsOn != null;
    }

    // Inhalt aus einer Änderung übernehmen; Status, Abhaken und Ersteller bleiben. Wartet der Artikel, gilt ein
    // geänderter Abstand ab dem letzten Kauf; ohne Wiederholung steht er wieder abgehakt auf der Liste.
    public ShoppingItem withContent(ShoppingItem changed, LocalDate today) {
        ShoppingItem updated = new ShoppingItem(id, changed.name().strip(), blankToNull(changed.quantity()),
                changed.category(), changed.urgent(), checked, status, createdBy, createdAt, checkedBy,
                changed.repeat(), checkedOn, returnsOn);
        return isWaiting() && updated.repeat() != null ? updated.afterShoppingTrip(today) : updated;
    }

    // Zurücknehmen holt auch einen wartenden Artikel sofort zurück auf die Liste
    public ShoppingItem withChecked(boolean newChecked, String by, LocalDate today) {
        return new ShoppingItem(id, name, quantity, category, urgent, newChecked, status, createdBy, createdAt,
                newChecked ? by : null, repeat, newChecked ? today : null, null);
    }

    public ShoppingItem approved() {
        return new ShoppingItem(id, name, quantity, category, urgent, checked, ShoppingItemStatus.APPROVED, createdBy,
                createdAt, checkedBy, repeat, checkedOn, returnsOn);
    }

    // "Abgehakte entfernen" bei einem gekauften wiederkehrenden Artikel: bei jedem Einkauf sofort wieder offen,
    // sonst wartet er bis zum Abstand ab dem Kauf. Abgehakte von vor den Wiederholungen haben keinen Kauftag.
    public ShoppingItem afterShoppingTrip(LocalDate today) {
        if (repeat.everyTrip()) {
            return reopened();
        }
        LocalDate due = repeat.nextDue(checkedOn == null ? today : checkedOn);
        return due.isAfter(today) ? new ShoppingItem(id, name, quantity, category, urgent, checked, status, createdBy,
                createdAt, checkedBy, repeat, checkedOn, due) : reopened();
    }

    // Abstand ab dem Kauf vorbei: zurück auf die Liste, ob schon entfernt (wartend) oder noch abgehakt
    public boolean isDueBack(LocalDate today) {
        return checked && repeat != null && !repeat.everyTrip() && checkedOn != null
                && !repeat.nextDue(checkedOn).isAfter(today);
    }

    // Wieder offen auf der Liste; "dringend" galt dem letzten Einkauf
    public ShoppingItem reopened() {
        return new ShoppingItem(id, name, quantity, category, false, false, status, createdBy, createdAt, null, repeat,
                null, null);
    }

    static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }
}
