package de.familyhub.assistant.tools;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Predicate;

import org.springframework.stereotype.Component;

import de.familyhub.assistant.AssistantArea;
import de.familyhub.assistant.llm.ToolSpec;
import de.familyhub.family.FamilyMember;
import de.familyhub.meals.MealAccess;
import de.familyhub.meals.MealEntry;
import de.familyhub.meals.MealEntryRepository;
import de.familyhub.permission.Module;
import de.familyhub.permission.Scope;

// Der Essensplan, so wie die fragende Person ihn sieht (Wünsche nur eigene bzw. als Elternteil alle)
@Component
public class MealTool implements AssistantTool {

    static final String NAME = "essensplan_lesen";
    static final int MAX_DAYS = 31;

    private final MealEntryRepository meals;
    private final MealAccess access;
    private final AgentRights rights;
    private final Clock clock;

    public MealTool(MealEntryRepository meals, MealAccess access, AgentRights rights, Clock clock) {
        this.meals = meals;
        this.access = access;
        this.rights = rights;
        this.clock = clock;
    }

    @Override
    public ToolSpec spec() {
        return new ToolSpec(NAME, "Liest den Essensplan in einem Zeitraum. Ohne Angaben: heute und die nächsten 6 "
                + "Tage. Wünsche (vorschlag: true) haben die Eltern noch nicht übernommen.",
                Map.of("type", "object", "properties", DateRange.schemaProperties(), "additionalProperties", false));
    }

    @Override
    public AssistantArea area() {
        return AssistantArea.ESSEN;
    }

    @Override
    public String label() {
        return "Essensplan";
    }

    @Override
    public boolean availableFor(FamilyMember viewer) {
        return rights.mayRead(viewer, Module.ESSEN, Scope.FAMILIE);
    }

    @Override
    public Object run(FamilyMember viewer, ToolArguments args) {
        DateRange range = DateRange.of(args, LocalDate.now(clock), 7, MAX_DAYS);
        Predicate<MealEntry> visible = access.visibilityFor(viewer);
        List<Map<String, Object>> rows = meals.findInRange(range.first(), range.last()).stream()
                .filter(visible)
                .sorted(Comparator.comparing(MealEntry::date).thenComparing(MealEntry::type))
                .map(MealTool::row)
                .toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("zeitraum", range.describe());
        result.put("essen", rows);
        return result;
    }

    private static Map<String, Object> row(MealEntry entry) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", entry.id());
        row.put("datum", entry.date().toString());
        row.put("mahlzeit", switch (entry.type()) {
            case FRUEHSTUECK -> "Frühstück";
            case MITTAGESSEN -> "Mittagessen";
            case ABENDESSEN -> "Abendessen";
            case SNACKS -> "Snacks";
        });
        row.put("gericht", entry.name());
        if (entry.isProposal()) {
            row.put("vorschlag", true);
        }
        return row;
    }
}
