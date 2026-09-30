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

// Gerichte-Sammlung mit Kochanleitungen und Wochenplan für die aktuelle Woche; dazu ein offener Wunsch von Lucas zum Ausprobieren.
final class SampleMeals {

    // day: Tag relativ zum Montag der aktuellen Woche; dish: Name aus DISHES oder, wenn nicht vorhanden, Freitext
    record SamplePlan(int day, MealType type, String dish, String username, boolean wish) {

        SamplePlan(int day, MealType type, String dish) {
            this(day, type, dish, "sarah", false);
        }
    }

    // Kochanleitung als Textblock, ein Schritt pro Zeile
    private static Dish dish(String name, int prepMinutes, int servings, String instructions,
            Ingredient... ingredients) {
        return new Dish(null, name, List.of(ingredients), instructions.strip(), prepMinutes, servings, null, null);
    }

    private static Ingredient i(String name, String quantity, ShoppingCategory category) {
        return new Ingredient(name, quantity, category);
    }

    static final List<Dish> DISHES = List.of(
            dish("Spaghetti Bolognese", 40, 4, """
                    Zwiebeln fein würfeln.
                    Hackfleisch in einer großen Pfanne krümelig anbraten, die Zwiebeln kurz mitbraten.
                    Passierte Tomaten dazugeben, mit Salz und Pfeffer würzen und 20 Minuten köcheln lassen.
                    Spaghetti in reichlich Salzwasser nach Packungsangabe kochen und abgießen.
                    Mit der Soße anrichten und Parmesan darüberreiben.
                    """,
                    i("Spaghetti", "500 g", VORRAT), i("Rinderhackfleisch", "500 g", FLEISCH),
                    i("Passierte Tomaten", "1 Packung", VORRAT), i("Zwiebeln", "2 Stück", GEMUESE),
                    i("Parmesan", "100 g", MILCHPRODUKTE)),
            dish("Gemüsecurry mit Reis", 35, 4, """
                    Reis nach Packungsangabe kochen.
                    Paprika und Zucchini in mundgerechte Stücke schneiden.
                    Currypaste in einem Topf kurz anrösten, das Gemüse dazugeben und 5 Minuten anbraten.
                    Kokosmilch angießen und 10 Minuten köcheln lassen, bis das Gemüse gar ist.
                    Mit Salz abschmecken und mit dem Reis servieren.
                    """,
                    i("Basmatireis", "250 g", VORRAT), i("Kokosmilch", "1 Dose", VORRAT),
                    i("Paprika", "2 Stück", GEMUESE), i("Zucchini", "1 Stück", GEMUESE),
                    i("Currypaste", "1 Glas", VORRAT)),
            dish("Hähnchen mit Ofengemüse", 50, 4, """
                    Backofen auf 200 °C Umluft vorheizen.
                    Kartoffeln und Karotten in Stücke schneiden und mit Öl, Salz und Rosmarin auf einem Blech mischen.
                    Das Gemüse 20 Minuten backen.
                    Hähnchenbrust salzen und pfeffern, aufs Blech legen und weitere 20 Minuten backen.
                    """,
                    i("Hähnchenbrust", "600 g", FLEISCH), i("Kartoffeln", "1 kg", GEMUESE),
                    i("Karotten", "500 g", GEMUESE), i("Rosmarin", "1 Bund", GEMUESE)),
            dish("Pfannkuchen mit Apfelmus", 30, 4, """
                    Mehl, Eier, Milch und eine Prise Salz zu einem glatten Teig verrühren und 10 Minuten ruhen lassen.
                    Etwas Butter in einer Pfanne erhitzen.
                    Eine Kelle Teig hineingeben, verteilen und von beiden Seiten goldbraun backen.
                    Mit Apfelmus servieren.
                    """,
                    i("Mehl", "250 g", VORRAT), i("Eier", "4 Stück", MILCHPRODUKTE),
                    i("Milch", "500 ml", MILCHPRODUKTE), i("Apfelmus", "1 Glas", VORRAT)),
            dish("Lachs mit Kartoffeln und Brokkoli", 30, 4, """
                    Kartoffeln schälen und in Salzwasser etwa 20 Minuten kochen.
                    Brokkoli in Röschen teilen und die letzten 5 Minuten mitgaren.
                    Lachsfilets salzen und in einer Pfanne mit etwas Öl je Seite 3 bis 4 Minuten braten.
                    Alles zusammen anrichten.
                    """,
                    i("Lachsfilet", "4 Stück", FLEISCH), i("Kartoffeln", "1 kg", GEMUESE),
                    i("Brokkoli", "1 Stück", GEMUESE)),
            dish("Pizza selbst gemacht", 30, 4, """
                    Backofen auf 220 °C Ober- und Unterhitze vorheizen.
                    Pizzateig auf Bleche mit Backpapier ausrollen.
                    Passierte Tomaten mit Salz und Oregano würzen und auf dem Teig verstreichen.
                    Mit Salami und zerzupftem Mozzarella belegen.
                    12 bis 15 Minuten backen, bis der Rand goldbraun ist.
                    """,
                    i("Pizzateig", "2 Rollen", VORRAT), i("Mozzarella", "2 Kugeln", MILCHPRODUKTE),
                    i("Passierte Tomaten", "1 Packung", VORRAT), i("Salami", "100 g", FLEISCH)),
            dish("Kartoffelsuppe mit Würstchen", 45, 4, """
                    Kartoffeln und Suppengemüse schälen und würfeln.
                    In einem Topf kurz anbraten, mit 1 Liter Brühe ablöschen und 20 Minuten köcheln lassen.
                    Einen Teil der Suppe pürieren und die Sahne einrühren.
                    Würstchen in Scheiben schneiden und 5 Minuten in der Suppe erwärmen.
                    """,
                    i("Kartoffeln", "1 kg", GEMUESE), i("Wiener Würstchen", "1 Packung", FLEISCH),
                    i("Suppengemüse", "1 Bund", GEMUESE), i("Sahne", "200 ml", MILCHPRODUKTE)),
            dish("Käsespätzle", 30, 4, """
                    Backofen auf 180 °C vorheizen.
                    Zwiebeln in Ringe schneiden und in Butter goldbraun braten.
                    Spätzle nach Packungsangabe kochen.
                    Spätzle und geriebenen Bergkäse abwechselnd in eine Auflaufform schichten.
                    10 Minuten überbacken und mit den Zwiebeln servieren.
                    """,
                    i("Spätzle", "500 g", VORRAT), i("Bergkäse", "200 g", MILCHPRODUKTE),
                    i("Zwiebeln", "3 Stück", GEMUESE)),
            dish("Wraps mit Hähnchen", 25, 4, """
                    Hähnchenbrust in Streifen schneiden, würzen und in einer Pfanne durchbraten.
                    Eisbergsalat in Streifen, Tomaten in Würfel schneiden.
                    Wraps kurz in der Pfanne erwärmen.
                    Mit Schmand bestreichen, mit Hähnchen, Salat und Tomaten füllen und einrollen.
                    """,
                    i("Tortilla-Wraps", "1 Packung", BACKWAREN), i("Hähnchenbrust", "400 g", FLEISCH),
                    i("Eisbergsalat", "1 Stück", GEMUESE), i("Tomaten", "4 Stück", GEMUESE),
                    i("Schmand", "1 Becher", MILCHPRODUKTE)),
            dish("Müsli mit Obst", 5, 4, """
                    Haferflocken in Schüsseln verteilen.
                    Joghurt darübergeben.
                    Beeren waschen und darauf verteilen.
                    """,
                    i("Haferflocken", "500 g", VORRAT), i("Joghurt", "500 g", MILCHPRODUKTE),
                    i("Beeren", "250 g", OBST)),
            dish("Rührei mit Brötchen", 15, 4, """
                    Brötchen im Ofen kurz aufbacken.
                    Eier mit einer Prise Salz verquirlen.
                    In einer Pfanne mit etwas Butter bei mittlerer Hitze unter Rühren langsam stocken lassen.
                    Mit gehacktem Schnittlauch bestreuen und mit den Brötchen servieren.
                    """,
                    i("Eier", "6 Stück", MILCHPRODUKTE), i("Brötchen", "6 Stück", BACKWAREN),
                    i("Schnittlauch", "1 Bund", GEMUESE)),
            dish("Obstsalat", 10, 4, """
                    Äpfel entkernen und würfeln, Bananen in Scheiben schneiden.
                    Weintrauben waschen und halbieren.
                    Alles in einer Schüssel mischen, nach Wunsch mit etwas Zitronensaft.
                    """,
                    i("Äpfel", "2 Stück", OBST), i("Bananen", "2 Stück", OBST),
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
