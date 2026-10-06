package de.familyhub.meals;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import de.familyhub.family.FamilyMember;
import de.familyhub.shopping.ShoppingCategory;
import de.familyhub.shopping.ShoppingAccess;
import de.familyhub.shopping.ShoppingItem;
import de.familyhub.shopping.ShoppingItemRepository;
import de.familyhub.shopping.ShoppingItemStatus;

// Zutaten aus dem Essensplan werden in einer eigenen Kategorie verwaltet.
@Component
public class MealShopping {

    private static final Pattern NUMERIC_QUANTITY = Pattern.compile("^([+-]?\\d+(?:[.,]\\d+)?)(\\s*)(.*)$");

    public record Transfer(List<String> added, List<String> skipped) {
    }

    private final DishRepository dishes;
    private final ShoppingItemRepository items;
    private final ShoppingAccess shoppingAccess;
    private final Clock clock;

    public MealShopping(DishRepository dishes, ShoppingItemRepository items, ShoppingAccess shoppingAccess,
            Clock clock) {
        this.dishes = dishes;
        this.items = items;
        this.shoppingAccess = shoppingAccess;
        this.clock = clock;
    }

    // entries: freigegebene Einträge in Plan-Reihenfolge; Wünsche und Freitext-Einträge haben keine Zutaten.
    public Transfer transfer(List<MealEntry> entries, FamilyMember viewer) {
        shoppingAccess.requireCreate(viewer);
        return apply(aggregate(entries), viewer, false);
    }

    // Die Wochenübernahme gleicht ihre verwaltete Kategorie mit dem aktuellen Essensplan ab.
    public Transfer synchronize(List<MealEntry> entries, FamilyMember viewer) {
        shoppingAccess.requireCreate(viewer);
        return apply(aggregate(entries), viewer, true);
    }

    private Transfer apply(Map<String, Ingredient> planned, FamilyMember viewer, boolean synchronize) {
        List<ShoppingItem> existing = items.findAll();
        Map<String, ShoppingItem> managed = existing.stream()
                .filter(i -> i.category() == ShoppingCategory.ZUTATEN_ESSENSPLANUNG && !i.isProposal())
                .collect(Collectors.toMap(i -> key(i.name()), Function.identity(), (first, ignored) -> first));

        List<String> added = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        List<ShoppingItem> toSave = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now(clock);

        for (Map.Entry<String, Ingredient> entry : planned.entrySet()) {
            String ingredientKey = entry.getKey();
            Ingredient ingredient = entry.getValue();
            ShoppingItem generated = managed.get(ingredientKey);
            if (generated != null) {
                if (synchronize) {
                    toSave.add(new ShoppingItem(generated.id(), generated.name(), ingredient.quantity(),
                            ShoppingCategory.ZUTATEN_ESSENSPLANUNG, generated.urgent(), generated.checked(),
                            generated.status(), generated.createdBy(), generated.createdAt(), generated.checkedBy()));
                }
                skipped.add(ingredient.name());
            } else {
                toSave.add(new ShoppingItem(null, ingredient.name(), ingredient.quantity(),
                        ShoppingCategory.ZUTATEN_ESSENSPLANUNG, false, false, ShoppingItemStatus.APPROVED, viewer.id(),
                        now.plus(toSave.size(), ChronoUnit.MILLIS), null));
                added.add(ingredient.name());
            }
        }

        if (synchronize) {
            List<ShoppingItem> stale = managed.entrySet().stream()
                    .filter(entry -> !planned.containsKey(entry.getKey()))
                    .map(entry -> entry.getValue()).toList();
            items.deleteAll(stale);
        }
        items.saveAll(toSave);
        return new Transfer(added, skipped);
    }

    private Map<String, Ingredient> aggregate(List<MealEntry> entries) {
        Map<String, Dish> dishById = dishes.findAllById(entries.stream()
                        .filter(e -> !e.isProposal() && e.dishId() != null).map(e -> e.dishId()).distinct().toList())
                .stream().collect(Collectors.toMap(dish -> dish.id(), Function.identity()));
        Map<String, Ingredient> planned = new LinkedHashMap<>();
        for (MealEntry entry : entries) {
            Dish dish = entry.isProposal() || entry.dishId() == null ? null : dishById.get(entry.dishId());
            if (dish == null) {
                continue;
            }
            for (Ingredient ingredient : dish.ingredients()) {
                String ingredientKey = key(ingredient.name());
                Ingredient previous = planned.get(ingredientKey);
                planned.put(ingredientKey, previous == null
                        ? new Ingredient(ingredient.name(), ingredient.quantity(), ShoppingCategory.ZUTATEN_ESSENSPLANUNG)
                        : new Ingredient(previous.name(), addQuantities(previous.quantity(), ingredient.quantity()),
                                ShoppingCategory.ZUTATEN_ESSENSPLANUNG));
            }
        }
        return planned;
    }

    private static String addQuantities(String first, String second) {
        if (first == null || first.isBlank()) {
            return second;
        }
        if (second == null || second.isBlank()) {
            return first;
        }
        Matcher firstMatch = NUMERIC_QUANTITY.matcher(first.strip());
        Matcher secondMatch = NUMERIC_QUANTITY.matcher(second.strip());
        if (firstMatch.matches() && secondMatch.matches()
                && firstMatch.group(3).strip().equalsIgnoreCase(secondMatch.group(3).strip())) {
            BigDecimal total = new BigDecimal(firstMatch.group(1).replace(',', '.'))
                    .add(new BigDecimal(secondMatch.group(1).replace(',', '.')));
            String unit = firstMatch.group(2) + firstMatch.group(3);
            return total.stripTrailingZeros().toPlainString() + unit;
        }
        return first.equalsIgnoreCase(second) ? "2 × " + first : first + " + " + second;
    }

    private static String key(String name) {
        return name.strip().toLowerCase(Locale.GERMAN);
    }
}
