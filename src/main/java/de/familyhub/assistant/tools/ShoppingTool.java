package de.familyhub.assistant.tools;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.springframework.stereotype.Component;

import de.familyhub.assistant.llm.ToolSpec;
import de.familyhub.family.FamilyMember;
import de.familyhub.permission.Module;
import de.familyhub.permission.Scope;
import de.familyhub.shopping.ShoppingAccess;
import de.familyhub.shopping.ShoppingItem;
import de.familyhub.shopping.ShoppingItemRepository;

// Die Einkaufsliste, so wie die fragende Person sie sieht (Vorschläge nur eigene bzw. als Elternteil alle)
@Component
public class ShoppingTool implements AssistantTool {

    static final String NAME = "einkaufsliste_lesen";

    private final ShoppingItemRepository items;
    private final ShoppingAccess access;
    private final AgentRights rights;

    public ShoppingTool(ShoppingItemRepository items, ShoppingAccess access, AgentRights rights) {
        this.items = items;
        this.access = access;
        this.rights = rights;
    }

    @Override
    public ToolSpec spec() {
        return new ToolSpec(NAME, "Liest die Einkaufsliste der Familie. Ohne Angaben nur, was noch gekauft werden "
                + "muss. Vorschläge (vorschlag: true) haben die Eltern noch nicht übernommen.",
                Map.of("type", "object", "properties", Map.of("auch_gekauft", Map.of("type", "boolean",
                        "description", "true: auch schon gekaufte (abgehakte) Artikel")),
                        "additionalProperties", false));
    }

    @Override
    public String label() {
        return "Einkaufsliste";
    }

    @Override
    public boolean availableFor(FamilyMember viewer) {
        return rights.mayRead(viewer, Module.EINKAUF, Scope.FAMILIE);
    }

    @Override
    public Object run(FamilyMember viewer, ToolArguments args) {
        boolean alsoChecked = args.flag("auch_gekauft", false);
        Predicate<ShoppingItem> visible = access.visibilityFor(viewer);
        List<Map<String, Object>> rows = items.findAllByOrderByCreatedAtAsc().stream()
                .filter(visible)
                .filter(item -> alsoChecked || !item.checked())
                .map(ShoppingTool::row)
                .toList();
        return Map.of("artikel", rows);
    }

    private static Map<String, Object> row(ShoppingItem item) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", item.id());
        row.put("name", item.name());
        if (item.quantity() != null && !item.quantity().isBlank()) {
            row.put("menge", item.quantity());
        }
        row.put("kategorie", item.category().name().toLowerCase());
        if (item.urgent()) {
            row.put("dringend", true);
        }
        if (item.checked()) {
            row.put("gekauft", true);
        }
        if (item.isProposal()) {
            row.put("vorschlag", true);
        }
        return row;
    }
}
