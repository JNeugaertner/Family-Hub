package de.familyhub.assistant.tools;

import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import de.familyhub.assistant.AssistantArea;
import de.familyhub.assistant.insights.EventConflict;
import de.familyhub.assistant.insights.InsightService;
import de.familyhub.assistant.llm.ToolSpec;
import de.familyhub.family.FamilyMember;
import de.familyhub.permission.Module;
import de.familyhub.permission.Scope;

// Terminkonflikte aus Phase 2: dieselben Regeln und dieselbe Sichtbarkeit wie die Hinweise in der Übersicht
@Component
public class ConflictTool implements AssistantTool {

    static final String NAME = "konflikte_pruefen";
    static final int MAX_DAYS = 92;

    private final InsightService insights;
    private final AgentRights rights;
    private final People people;
    private final Clock clock;

    public ConflictTool(InsightService insights, AgentRights rights, People people, Clock clock) {
        this.insights = insights;
        this.rights = rights;
        this.people = people;
        this.clock = clock;
    }

    @Override
    public ToolSpec spec() {
        return new ToolSpec(NAME, "Findet Terminkonflikte: dieselbe Person hat zwei Termine zur gleichen Zeit, auch "
                + "mit offenen Vorschlägen. Ganztägige Termine zählen nicht. Ohne Angaben: heute und die nächsten "
                + "13 Tage.",
                Map.of("type", "object", "properties", DateRange.schemaProperties(), "additionalProperties", false));
    }

    @Override
    public AssistantArea area() {
        return AssistantArea.KALENDER;
    }

    @Override
    public String label() {
        return "Terminkonflikte";
    }

    @Override
    public boolean availableFor(FamilyMember viewer) {
        return rights.mayRead(viewer, Module.KALENDER, Scope.EIGEN);
    }

    @Override
    public Object run(FamilyMember viewer, ToolArguments args) {
        DateRange range = DateRange.of(args, LocalDate.now(clock), 14, MAX_DAYS);
        Map<String, String> names = people.names();
        List<Map<String, Object>> rows = insights.conflictsFor(viewer, range.start(), range.end()).stream()
                .map(c -> row(c, names))
                .toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("zeitraum", range.describe());
        result.put("konflikte", rows);
        return result;
    }

    private static Map<String, Object> row(EventConflict c, Map<String, String> names) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("personen", People.namesOf(c.memberIds(), names));
        row.put("termin1", event(c.first()));
        row.put("termin2", event(c.second()));
        return row;
    }

    private static Map<String, Object> event(EventConflict.EventRef e) {
        Map<String, Object> event = new LinkedHashMap<>();
        event.put("titel", e.title());
        event.put("beginn", e.start().toString());
        event.put("ende", e.end().toString());
        if (e.proposal()) {
            event.put("vorschlag", true);
        }
        return event;
    }
}
