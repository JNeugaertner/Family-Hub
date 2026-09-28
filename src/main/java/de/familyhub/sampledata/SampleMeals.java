package de.familyhub.sampledata;

import static de.familyhub.meals.MealType.ABENDESSEN;
import static de.familyhub.meals.MealType.FRUEHSTUECK;
import static de.familyhub.meals.MealType.MITTAGESSEN;
import static de.familyhub.meals.MealType.SNACKS;
import static de.familyhub.shopping.ShoppingCategory.BACKWAREN;
import static de.familyhub.shopping.ShoppingCategory.FLEISCH;
import static de.familyhub.shopping.ShoppingCategory.GEMUESE;
import static de.familyhub.shopping.ShoppingCategory.MILCHPRODUKTE;
import static de.familyhub.shopping.ShoppingCategory.OBST;
import static de.familyhub.shopping.ShoppingCategory.VORRAT;

import java.util.List;

import de.familyhub.meals.Dish;
import de.familyhub.meals.Ingredient;
import de.familyhub.meals.MealType;
import de.familyhub.shopping.ShoppingCategory;

// Gerichte-Sammlung und Wochenplan für die aktuelle Woche; dazu ein offener Wunsch von Lucas zum Ausprobieren.
final class SampleMeals {

    // day: Tag relativ zum Montag der aktuellen Woche; dish: Name aus DISHES oder, wenn nicht vorhanden, Freitext
    record SamplePlan(int day, MealType type, String dish, String username, boolean wish) {

        SamplePlan(int day, MealType type, String dish) {
            this(day, type, dish, "sarah", false);
        }
    }

    private static Dish dish(String name, Ingredient... ingredients) {
        return new Dish(null, name, List.of(ingredients), null, null);
    }

    private static Ingredient i(String name, String quantity, ShoppingCategory category) {
        return new Ingredient(name, quantity, category);
    }

    static final List<Dish> DISHES = List.of(
            dish("Spaghetti Bolognese", i("Spaghetti", "500 g", VORRAT), i("Rinderhackfleisch", "500 g", FLEISCH),
                    i("Passierte Tomaten", "1 Packung", VORRAT), i("Zwiebeln", "2 Stück", GEMUESE),
                    i("Parmesan", "100 g", MILCHPRODUKTE)),
            dish("Gemüsecurry mit Reis", i("Basmatireis", "250 g", VORRAT), i("Kokosmilch", "1 Dose", VORRAT),
                    i("Paprika", "2 Stück", GEMUESE), i("Zucchini", "1 Stück", GEMUESE),
                    i("Currypaste", "1 Glas", VORRAT)),
            dish("Hähnchen mit Ofengemüse", i("Hähnchenbrust", "600 g", FLEISCH), i("Kartoffeln", "1 kg", GEMUESE),
                    i("Karotten", "500 g", GEMUESE), i("Rosmarin", "1 Bund", GEMUESE)),
            dish("Pfannkuchen mit Apfelmus", i("Mehl", "250 g", VORRAT), i("Eier", "4 Stück", MILCHPRODUKTE),
                    i("Milch", "500 ml", MILCHPRODUKTE), i("Apfelmus", "1 Glas", VORRAT)),
            dish("Lachs mit Kartoffeln und Brokkoli", i("Lachsfilet", "4 Stück", FLEISCH),
                    i("Kartoffeln", "1 kg", GEMUESE), i("Brokkoli", "1 Stück", GEMUESE)),
            dish("Pizza selbst gemacht", i("Pizzateig", "2 Rollen", VORRAT), i("Mozzarella", "2 Kugeln", MILCHPRODUKTE),
                    i("Passierte Tomaten", "1 Packung", VORRAT), i("Salami", "100 g", FLEISCH)),
            dish("Kartoffelsuppe mit Würstchen", i("Kartoffeln", "1 kg", GEMUESE),
                    i("Wiener Würstchen", "1 Packung", FLEISCH), i("Suppengemüse", "1 Bund", GEMUESE),
                    i("Sahne", "200 ml", MILCHPRODUKTE)),
            dish("Käsespätzle", i("Spätzle", "500 g", VORRAT), i("Bergkäse", "200 g", MILCHPRODUKTE),
                    i("Zwiebeln", "3 Stück", GEMUESE)),
            dish("Wraps mit Hähnchen", i("Tortilla-Wraps", "1 Packung", BACKWAREN),
                    i("Hähnchenbrust", "400 g", FLEISCH), i("Eisbergsalat", "1 Stück", GEMUESE),
                    i("Tomaten", "4 Stück", GEMUESE), i("Schmand", "1 Becher", MILCHPRODUKTE)),
            dish("Müsli mit Obst", i("Haferflocken", "500 g", VORRAT), i("Joghurt", "500 g", MILCHPRODUKTE),
                    i("Beeren", "250 g", OBST)),
            dish("Rührei mit Brötchen", i("Eier", "6 Stück", MILCHPRODUKTE), i("Brötchen", "6 Stück", BACKWAREN),
                    i("Schnittlauch", "1 Bund", GEMUESE)),
            dish("Obstsalat", i("Äpfel", "2 Stück", OBST), i("Bananen", "2 Stück", OBST),
                    i("Weintrauben", "250 g", OBST)));

    static final List<SamplePlan> PLAN = List.of(
            new SamplePlan(0, FRUEHSTUECK, "Müsli mit Obst"),
            new SamplePlan(1, FRUEHSTUECK, "Brot mit Käse"),
            new SamplePlan(2, FRUEHSTUECK, "Müsli mit Obst"),
            new SamplePlan(3, FRUEHSTUECK, "Brot mit Käse"),
            new SamplePlan(4, FRUEHSTUECK, "Müsli mit Obst"),
            new SamplePlan(5, FRUEHSTUECK, "Rührei mit Brötchen"),
            new SamplePlan(6, FRUEHSTUECK, "Rührei mit Brötchen"),
            new SamplePlan(2, MITTAGESSEN, "Reste vom Vortag"),
            new SamplePlan(5, MITTAGESSEN, "Kartoffelsuppe mit Würstchen"),
            new SamplePlan(6, MITTAGESSEN, "Essen bei Oma"),
            new SamplePlan(0, ABENDESSEN, "Spaghetti Bolognese"),
            new SamplePlan(1, ABENDESSEN, "Gemüsecurry mit Reis"),
            new SamplePlan(2, ABENDESSEN, "Wraps mit Hähnchen"),
            new SamplePlan(3, ABENDESSEN, "Lachs mit Kartoffeln und Brokkoli"),
            new SamplePlan(4, ABENDESSEN, "Pizza selbst gemacht"),
            new SamplePlan(5, ABENDESSEN, "Hähnchen mit Ofengemüse"),
            new SamplePlan(6, ABENDESSEN, "Käsespätzle"),
            new SamplePlan(0, SNACKS, "Obstsalat"),
            new SamplePlan(2, SNACKS, "Apfelschnitze"),
            new SamplePlan(4, SNACKS, "Popcorn zum Filmabend"),
            new SamplePlan(6, SNACKS, "Kuchen bei Oma"),
            new SamplePlan(3, ABENDESSEN, "Pfannkuchen mit Apfelmus", "lucas", true));

    private SampleMeals() {
    }
}
