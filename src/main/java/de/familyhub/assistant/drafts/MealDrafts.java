package de.familyhub.assistant.drafts;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Component;

import de.familyhub.assistant.llm.ToolSpec;
import de.familyhub.assistant.tools.AgentRights;
import de.familyhub.assistant.tools.ToolArguments;
import de.familyhub.assistant.tools.ToolException;
import de.familyhub.family.FamilyMember;
import de.familyhub.meals.Dish;
import de.familyhub.meals.DishRepository;
import de.familyhub.meals.MealAccess;
import de.familyhub.meals.MealController;
import de.familyhub.meals.MealEntry;
import de.familyhub.meals.MealEntryRepository;
import de.familyhub.meals.MealStatus;
import de.familyhub.permission.Module;

// Entwürfe für den Essensplan: eine Mahlzeit eintragen (ersetzt wie auf der Seite den bisherigen Eintrag) oder einen
// Eintrag entfernen. Kinder äußern wie dort einen Wunsch. Gerichte aus der Sammlung werden am Namen erkannt.
@Component
public class MealDrafts extends DraftTool {

    static final String NAME = "essen_entwerfen";

    private static final List<String> ACTIONS = List.of("neu", "loeschen");
    private static final List<String> TYPES = List.of("fruehstueck", "mittagessen", "abendessen", "snacks");
    private static final Set<String> KNOWN = Set.of("aktion", "id", "datum", "mahlzeit", "gericht");
    private static final Set<String> CREATE_FIELDS = Set.of("date", "type", "name");
    private static final List<String> SNAPSHOT = List.of("date", "type", "name");

    private final MealEntryRepository meals;
    private final DishRepository dishes;
    private final MealAccess access;
    private final MealController controller;
    private final AgentRights rights;
    private final DraftSupport support;

    public MealDrafts(DraftRepository drafts, Clock clock, MealEntryRepository meals, DishRepository dishes,
            MealAccess access, MealController controller, AgentRights rights, DraftSupport support) {
        super(drafts, clock);
        this.meals = meals;
        this.dishes = dishes;
        this.access = access;
        this.controller = controller;
        this.rights = rights;
        this.support = support;
    }

    @Override
    public DraftKind kind() {
        return DraftKind.MEAL;
    }

    @Override
    public ToolSpec spec() {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("aktion", Map.of("type", "string", "enum", ACTIONS));
        entry.put("id", Map.of("type", "string", "description", "Bei loeschen: id aus essensplan_lesen"));
        entry.put("datum", Map.of("type", "string", "description", "JJJJ-MM-TT"));
        entry.put("mahlzeit", Map.of("type", "string", "enum", TYPES));
        entry.put("gericht", Map.of("type", "string",
                "description", "Name eines Gerichts aus der Sammlung oder freier Text"));
        return new ToolSpec(NAME, "Bereitet Einträge im Essensplan als Entwurf vor, den die Person selbst übernimmt: "
                + "neu trägt eine Mahlzeit ein und ersetzt dabei den bisherigen Eintrag dieser Mahlzeit; loeschen "
                + "entfernt einen Eintrag. Mehrere Mahlzeiten in einen Aufruf.",
                Map.of("type", "object", "properties", Map.of("eintraege", Map.of("type", "array",
                        "items", Map.of("type", "object", "properties", entry, "required", List.of("aktion"),
                                "additionalProperties", false))),
                        "required", List.of("eintraege"), "additionalProperties", false));
    }

    @Override
    public String label() {
        return "Essensplan";
    }

    @Override
    public boolean availableFor(FamilyMember viewer) {
        return rights.mayDraft(viewer, Module.ESSEN);
    }

    @Override
    public Set<String> editable(DraftItem.Action action) {
        return action == DraftItem.Action.CREATE ? CREATE_FIELDS : Set.of();
    }

    @Override
    protected Prepared prepare(FamilyMember viewer, ToolArguments entry) {
        entry.requireOnly(KNOWN);
        return switch (action(entry, ACTIONS)) {
            case "neu" -> create(viewer, entry);
            default -> delete(viewer, entry);
        };
    }

    private Prepared create(FamilyMember viewer, ToolArguments entry) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("date", entry.date("datum").orElseThrow(() -> new ToolException("datum fehlt.")).toString());
        data.put("type", entry.choice("mahlzeit", TYPES).orElseThrow(() -> new ToolException(
                "mahlzeit fehlt: " + String.join(", ", TYPES) + ".")));
        String wanted = entry.text("gericht").orElseThrow(() -> new ToolException("gericht fehlt."));
        Optional<Dish> dish = dish(wanted);
        dish.ifPresent(d -> data.put("dishId", d.id()));
        data.put("name", dish.map(Dish::name).orElse(wanted));
        MealController.MealRequest request = support.read(data, MealController.MealRequest.class);
        MealStatus status = access.statusForNewEntry(viewer);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("aktion", "neu");
        summary.put("datum", data.get("date"));
        summary.put("mahlzeit", data.get("type"));
        summary.put("gericht", data.get("name"));
        summary.put("aus_der_sammlung", dish.isPresent());
        if (status == MealStatus.PROPOSED) {
            summary.put("wird", "ein Wunsch, den ein Elternteil übernehmen muss");
        } else {
            meals.findByDateAndTypeAndStatus(request.date(), request.type(), MealStatus.APPROVED).stream()
                    .findFirst().ifPresent(replaced -> summary.put("ersetzt", replaced.name()));
        }
        return new Prepared(DraftItem.create(data), summary);
    }

    private Prepared delete(FamilyMember viewer, ToolArguments entry) {
        String id = id(entry);
        MealEntry existing = meals.findById(id).filter(access.visibilityFor(viewer))
                .orElseThrow(() -> new ToolException("Einen Eintrag mit der id " + id + " gibt es nicht."));
        access.requireDelete(viewer, existing);
        return new Prepared(DraftItem.delete(existing.id(), support.snapshot(existing, SNAPSHOT)),
                Map.of("aktion", "loeschen", "datum", existing.date().toString(), "gericht", existing.name()));
    }

    @Override
    public String apply(FamilyMember viewer, DraftItem item, Map<String, Object> data) {
        if (item.action() == DraftItem.Action.DELETE) {
            controller.delete(item.targetId());
            return outcome(false);
        }
        // Hat jemand auf der Karte einen anderen Namen eingetragen, ist es kein Gericht der Sammlung mehr
        Map<String, Object> request = new LinkedHashMap<>(data);
        String name = String.valueOf(request.get("name"));
        Object dishId = request.get("dishId");
        if (dishId != null && dishes.findById(dishId.toString()).map(Dish::name).filter(name::equals).isEmpty()) {
            request.remove("dishId");
        }
        MealEntry saved = controller.create(support.read(request, MealController.MealRequest.class)).getBody();
        support.markViaAssistant(saved.id(), MealEntry.class);
        return outcome(saved.isProposal());
    }

    private Optional<Dish> dish(String name) {
        return dishes.findAll().stream().filter(d -> d.name().equalsIgnoreCase(name.strip())).findFirst();
    }
}
