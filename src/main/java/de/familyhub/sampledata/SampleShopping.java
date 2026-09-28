package de.familyhub.sampledata;

import static de.familyhub.shopping.ShoppingCategory.BACKWAREN;
import static de.familyhub.shopping.ShoppingCategory.FLEISCH;
import static de.familyhub.shopping.ShoppingCategory.GEMUESE;
import static de.familyhub.shopping.ShoppingCategory.GETRAENKE;
import static de.familyhub.shopping.ShoppingCategory.HAUSHALT;
import static de.familyhub.shopping.ShoppingCategory.MILCHPRODUKTE;
import static de.familyhub.shopping.ShoppingCategory.OBST;
import static de.familyhub.shopping.ShoppingCategory.SNACKS;
import static de.familyhub.shopping.ShoppingCategory.VORRAT;

import java.util.List;

import de.familyhub.shopping.ShoppingCategory;

// Einkaufsliste aus dem Figma-UI (data.ts), auf Deutsch; dazu ein offener Vorschlag von Lucas zum Ausprobieren.
final class SampleShopping {

    record SampleItem(String name, String quantity, ShoppingCategory category, boolean urgent, boolean checked,
            String username, boolean proposal) {

        SampleItem(String name, String quantity, ShoppingCategory category, String username) {
            this(name, quantity, category, false, false, username, false);
        }
    }

    static final List<SampleItem> ITEMS = List.of(
            new SampleItem("Bio-Milch", "2 × 1 l", MILCHPRODUKTE, true, false, "sarah", false),
            new SampleItem("Vollkornbrot", "1 Laib", BACKWAREN, "sarah"),
            new SampleItem("Hähnchenbrust", "1 kg", FLEISCH, "mike"),
            new SampleItem("Brokkoli", "1 Stück", GEMUESE, false, true, "sarah", false),
            new SampleItem("Apfelsaft", "1,5 l", GETRAENKE, "mike"),
            new SampleItem("Griechischer Joghurt", "4 Becher", MILCHPRODUKTE, "emma"),
            new SampleItem("Penne", "500 g", VORRAT, false, true, "mike", false),
            new SampleItem("Tomatensauce", "2 Gläser", VORRAT, "mike"),
            new SampleItem("Bananen", "1 Staude", OBST, "sarah"),
            new SampleItem("Käse", "200 g", MILCHPRODUKTE, "sarah"),
            new SampleItem("Freiland-Eier", "10 Stück", MILCHPRODUKTE, true, false, "sarah", false),
            new SampleItem("Spülmittel", "1 Flasche", HAUSHALT, "mike"),
            new SampleItem("Schokolade", "1 Tafel", SNACKS, false, false, "lucas", true));

    private SampleShopping() {
    }
}
