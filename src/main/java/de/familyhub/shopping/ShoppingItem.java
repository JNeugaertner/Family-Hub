package de.familyhub.shopping;

import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.mongodb.core.mapping.Document;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.swagger.v3.oas.annotations.media.Schema;
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

        @Schema(accessMode = Schema.AccessMode.READ_ONLY,
                description = "Über den KI-Assistenten entstanden: ein Mensch hat einen Entwurf übernommen")
        Boolean viaAssistant) {

    @PersistenceCreator
    public ShoppingItem {
        urgent = Boolean.TRUE.equals(urgent);
        checked = Boolean.TRUE.equals(checked);
        status = status == null ? ShoppingItemStatus.APPROVED : status;
        viaAssistant = Boolean.TRUE.equals(viaAssistant);
    }

    public ShoppingItem(String id, String name, String quantity, ShoppingCategory category, Boolean urgent,
            Boolean checked, ShoppingItemStatus status, String createdBy, LocalDateTime createdAt, String checkedBy) {
        this(id, name, quantity, category, urgent, checked, status, createdBy, createdAt, checkedBy, false);
    }

    @JsonIgnore
    public boolean isProposal() {
        return status == ShoppingItemStatus.PROPOSED;
    }

    // Inhalt aus einer Änderung übernehmen; Status, Abhaken und Ersteller bleiben.
    public ShoppingItem withContent(ShoppingItem changed) {
        return new ShoppingItem(id, changed.name().strip(), blankToNull(changed.quantity()), changed.category(),
                changed.urgent(), checked, status, createdBy, createdAt, checkedBy, viaAssistant);
    }

    public ShoppingItem withChecked(boolean newChecked, String by) {
        return new ShoppingItem(id, name, quantity, category, urgent, newChecked, status, createdBy, createdAt,
                newChecked ? by : null, viaAssistant);
    }

    public ShoppingItem approved() {
        return new ShoppingItem(id, name, quantity, category, urgent, checked, ShoppingItemStatus.APPROVED, createdBy,
                createdAt, checkedBy, viaAssistant);
    }

    static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }
}
