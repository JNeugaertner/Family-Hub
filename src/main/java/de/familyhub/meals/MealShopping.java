package de.familyhub.meals;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import de.familyhub.family.FamilyMember;
import de.familyhub.shopping.ShoppingAccess;
import de.familyhub.shopping.ShoppingItem;
import de.familyhub.shopping.ShoppingItemRepository;
import de.familyhub.shopping.ShoppingItemStatus;

// Zutaten aus dem Essensplan auf die Einkaufsliste. Was dort schon offen steht (nicht abgehakt, gleicher Name ohne
// Rücksicht auf Groß- und Kleinschreibung), wird nicht doppelt angelegt; dasselbe gilt für Zutaten, die in mehreren
// Gerichten vorkommen.
@Component
public class MealShopping {

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
        Map<String, Dish> dishById = dishes.findAllById(entries.stream()
                        .filter(e -> !e.isProposal() && e.dishId() != null).map(MealEntry::dishId).distinct().toList())
                .stream().collect(Collectors.toMap(Dish::id, Function.identity()));
        Set<String> onList = items.findAll().stream()
                .filter(i -> !i.checked() && !i.isProposal()).map(i -> key(i.name())).collect(Collectors.toSet());

        Set<String> seen = new HashSet<>();
        List<String> added = new ArrayList<>();
        List<String> skipped = new ArrayList<>();
        List<ShoppingItem> newItems = new ArrayList<>();
        LocalDateTime now = LocalDateTime.now(clock);
        for (MealEntry entry : entries) {
            Dish dish = entry.isProposal() || entry.dishId() == null ? null : dishById.get(entry.dishId());
            if (dish == null) {
                continue;
            }
            for (Ingredient ingredient : dish.ingredients()) {
                String key = key(ingredient.name());
                if (!seen.add(key)) {
                    continue;
                }
                if (onList.contains(key)) {
                    skipped.add(ingredient.name());
                    continue;
                }
                // Millisekunden Abstand, damit die Liste die Reihenfolge des Plans behält
                newItems.add(new ShoppingItem(null, ingredient.name(), ingredient.quantity(), ingredient.category(),
                        false, false, ShoppingItemStatus.APPROVED, viewer.id(),
                        now.plus(newItems.size(), ChronoUnit.MILLIS), null));
                added.add(ingredient.name());
            }
        }
        items.saveAll(newItems);
        return new Transfer(added, skipped);
    }

    private static String key(String name) {
        return name.strip().toLowerCase(Locale.GERMAN);
    }
}
