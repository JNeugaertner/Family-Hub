package de.familyhub.meals;

import de.familyhub.shopping.ShoppingCategory;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// Zutat eines Gerichts; wird so, wie sie ist, als Artikel auf die Einkaufsliste übernommen.
public record Ingredient(
        @NotBlank(message = "Zutat darf nicht leer sein")
        @Size(max = 80, message = "Zutat darf höchstens 80 Zeichen lang sein")
        String name,

        @Size(max = 30, message = "Menge darf höchstens 30 Zeichen lang sein")
        @Schema(description = "Freitext, z. B. 500 g")
        String quantity,

        @NotNull(message = "Kategorie ist Pflicht")
        ShoppingCategory category) {

    Ingredient normalized() {
        return new Ingredient(name.strip(), quantity == null || quantity.isBlank() ? null : quantity.strip(), category);
    }
}
