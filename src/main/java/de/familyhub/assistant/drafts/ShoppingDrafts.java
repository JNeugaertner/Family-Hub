package de.familyhub.assistant.drafts;

import java.time.Clock;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import de.familyhub.assistant.llm.ToolSpec;
import de.familyhub.assistant.tools.AgentRights;
import de.familyhub.assistant.tools.ToolArguments;
import de.familyhub.assistant.tools.ToolException;
import de.familyhub.family.FamilyMember;
import de.familyhub.permission.Module;
import de.familyhub.shopping.ShoppingAccess;
import de.familyhub.shopping.ShoppingCategory;
import de.familyhub.shopping.ShoppingController;
import de.familyhub.shopping.ShoppingItem;
import de.familyhub.shopping.ShoppingItemRepository;
import de.familyhub.shopping.ShoppingItemStatus;
import de.familyhub.web.ApiException;

// Entwürfe für die Einkaufsliste: Artikel hinzufügen, ändern (auch als gekauft abhaken), löschen. Kinder machen
// wie auf der Seite Einkauf einen Vorschlag. "Zutaten Essensplanung" füllt nur der Essensplan.
@Component
public class ShoppingDrafts extends DraftTool {

    static final String NAME = "einkauf_entwerfen";

    private static final List<String> ACTIONS = List.of("neu", "aendern", "loeschen");
    private static final List<String> CATEGORIES = Arrays.stream(ShoppingCategory.values())
            .filter(c -> c != ShoppingCategory.ZUTATEN_ESSENSPLANUNG)
            .map(c -> c.name().toLowerCase(Locale.ROOT))
            .toList();
    private static final Set<String> KNOWN = Set.of("aktion", "id", "name", "menge", "kategorie", "dringend",
            "gekauft");
    private static final Set<String> CREATE_FIELDS = Set.of("name", "quantity", "category", "urgent");
    private static final Set<String> UPDATE_FIELDS = Set.of("name", "quantity", "category", "urgent", "checked");
    private static final List<String> SNAPSHOT = List.of("name", "quantity", "category", "urgent", "checked");

    private final ShoppingItemRepository items;
    private final ShoppingAccess access;
    private final ShoppingController controller;
    private final AgentRights rights;
    private final DraftSupport support;

    public ShoppingDrafts(DraftRepository drafts, Clock clock, ShoppingItemRepository items, ShoppingAccess access,
            ShoppingController controller, AgentRights rights, DraftSupport support) {
        super(drafts, clock);
        this.items = items;
        this.access = access;
        this.controller = controller;
        this.rights = rights;
        this.support = support;
    }

    @Override
    public DraftKind kind() {
        return DraftKind.SHOPPING;
    }

    @Override
    public ToolSpec spec() {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("aktion", Map.of("type", "string", "enum", ACTIONS));
        entry.put("id", Map.of("type", "string", "description",
                "Bei aendern und loeschen: id aus einkaufsliste_lesen"));
        entry.put("name", Map.of("type", "string"));
        entry.put("menge", Map.of("type", "string", "description", "Freitext, z. B. 2 l; nur, wenn die Person sie nennt"));
        entry.put("kategorie", Map.of("type", "string", "enum", CATEGORIES,
                "description", "Pflicht bei neu; wähle selbst die passendste"));
        entry.put("dringend", Map.of("type", "boolean"));
        entry.put("gekauft", Map.of("type", "boolean", "description", "Nur bei aendern: true hakt den Artikel ab"));
        return new ToolSpec(NAME, "Bereitet Artikel der Einkaufsliste zum Hinzufügen, Ändern (auch abhaken) oder "
                + "Löschen als Entwurf vor, den die Person selbst übernimmt. Mehrere Artikel in einen Aufruf.",
                Map.of("type", "object", "properties", Map.of("eintraege", Map.of("type", "array",
                        "items", Map.of("type", "object", "properties", entry, "required", List.of("aktion"),
                                "additionalProperties", false))),
                        "required", List.of("eintraege"), "additionalProperties", false));
    }

    @Override
    public String label() {
        return "Einkaufsliste";
    }

    @Override
    public boolean availableFor(FamilyMember viewer) {
        return rights.mayDraft(viewer, Module.EINKAUF);
    }

    @Override
    public Set<String> editable(DraftItem.Action action) {
        return switch (action) {
            case CREATE -> CREATE_FIELDS;
            case UPDATE -> UPDATE_FIELDS;
            case DELETE -> Set.of();
        };
    }

    @Override
    protected Prepared prepare(FamilyMember viewer, ToolArguments entry) {
        entry.requireOnly(KNOWN);
        return switch (action(entry, ACTIONS)) {
            case "neu" -> create(viewer, entry);
            case "aendern" -> update(viewer, entry);
            default -> delete(viewer, entry);
        };
    }

    private Prepared create(FamilyMember viewer, ToolArguments entry) {
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("name", entry.text("name").orElseThrow(() -> new ToolException("name fehlt.")));
        entry.text("menge").ifPresent(quantity -> data.put("quantity", quantity));
        data.put("category", entry.choice("kategorie", CATEGORIES).orElseThrow(() -> new ToolException(
                "kategorie fehlt: " + String.join(", ", CATEGORIES) + ".")));
        data.put("urgent", entry.flag("dringend", false));
        ShoppingItem item = support.read(data, ShoppingItem.class);
        ShoppingItemStatus status = access.statusForNewItem(viewer);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("aktion", "neu");
        summary.put("name", item.name());
        if (status == ShoppingItemStatus.PROPOSED) {
            summary.put("wird", "ein Vorschlag, den ein Elternteil übernehmen muss");
        }
        boolean listed = items.findAllByOrderByCreatedAtAsc().stream().filter(access.visibilityFor(viewer))
                .anyMatch(other -> !other.checked() && other.name().equalsIgnoreCase(item.name().strip()));
        if (listed) {
            summary.put("steht_schon_auf_der_liste", true);
        }
        return new Prepared(DraftItem.create(data), summary);
    }

    private Prepared update(FamilyMember viewer, ToolArguments entry) {
        ShoppingItem existing = find(viewer, id(entry));
        Map<String, Object> changes = new LinkedHashMap<>();
        entry.text("name").ifPresent(name -> changes.put("name", name));
        entry.text("menge").ifPresent(quantity -> changes.put("quantity", quantity));
        entry.choice("kategorie", CATEGORIES).ifPresent(category -> changes.put("category", category));
        entry.optionalFlag("dringend").ifPresent(urgent -> changes.put("urgent", urgent));
        entry.optionalFlag("gekauft").ifPresent(checked -> {
            if (existing.isProposal()) {
                throw ApiException.conflict("Ein Vorschlag muss erst übernommen werden.");
            }
            changes.put("checked", checked);
        });
        if (changes.isEmpty()) {
            throw new ToolException("Nichts zu ändern angegeben.");
        }
        access.requireEdit(viewer);
        support.merge(existing, changes, ShoppingItem.class);
        return new Prepared(DraftItem.update(existing.id(), changes, support.snapshot(existing, SNAPSHOT)),
                Map.of("aktion", "aendern", "name", existing.name()));
    }

    private Prepared delete(FamilyMember viewer, ToolArguments entry) {
        ShoppingItem existing = find(viewer, id(entry));
        access.requireDelete(viewer, existing);
        return new Prepared(DraftItem.delete(existing.id(), support.snapshot(existing, SNAPSHOT)),
                Map.of("aktion", "loeschen", "name", existing.name()));
    }

    @Override
    public String apply(FamilyMember viewer, DraftItem item, Map<String, Object> data) {
        return switch (item.action()) {
            case CREATE -> {
                ShoppingItem saved = controller.create(support.read(data, ShoppingItem.class)).getBody();
                support.markViaAssistant(saved.id(), ShoppingItem.class);
                yield outcome(saved.isProposal());
            }
            case UPDATE -> {
                Map<String, Object> content = new LinkedHashMap<>(data);
                Object checked = content.remove("checked");
                if (!content.isEmpty()) {
                    controller.update(item.targetId(),
                            support.merge(find(viewer, item.targetId()), content, ShoppingItem.class));
                }
                if (checked != null) {
                    controller.check(item.targetId(),
                            new ShoppingController.CheckedChange(Boolean.TRUE.equals(checked)));
                }
                yield outcome(false);
            }
            case DELETE -> {
                controller.delete(item.targetId());
                yield outcome(false);
            }
        };
    }

    private ShoppingItem find(FamilyMember viewer, String id) {
        return items.findById(id).filter(access.visibilityFor(viewer))
                .orElseThrow(() -> new ToolException("Einen Artikel mit der id " + id + " gibt es nicht."));
    }
}
