package de.familyhub.assistant.tools;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import de.familyhub.assistant.AssistantArea;
import de.familyhub.assistant.llm.ToolSpec;
import de.familyhub.family.FamilyMember;
import de.familyhub.permission.Module;
import de.familyhub.permission.Scope;
import de.familyhub.waste.WasteCollection;
import de.familyhub.waste.WasteCollectionService;

// Die nächsten Abholtermine der Müllabfuhr und wer die Tonne rausbringt
@Component
public class WasteTool implements AssistantTool {

    static final String NAME = "muellabfuhr_lesen";
    static final int MAX_PICKUPS = 8;

    private final WasteCollectionService waste;
    private final AgentRights rights;
    private final People people;
    private final Clock clock;

    public WasteTool(WasteCollectionService waste, AgentRights rights, People people, Clock clock) {
        this.waste = waste;
        this.rights = rights;
        this.people = people;
        this.clock = clock;
    }

    @Override
    public ToolSpec spec() {
        return new ToolSpec(NAME, "Liest die nächsten Abholtermine der Müllabfuhr (ab heute) und wer die Tonne "
                + "rausbringt. Die Aufgabe dafür ist am Vortag fällig.",
                Map.of("type", "object", "properties", Map.of(), "additionalProperties", false));
    }

    @Override
    public AssistantArea area() {
        return AssistantArea.WETTER_MUELL;
    }

    @Override
    public String label() {
        return "Müllabfuhr";
    }

    @Override
    public boolean availableFor(FamilyMember viewer) {
        return rights.mayRead(viewer, Module.MUELL, Scope.FREIGEGEBEN);
    }

    @Override
    public Object run(FamilyMember viewer, ToolArguments args) {
        WasteCollection collection = waste.current();
        if (collection == null) {
            return Map.of("hinweis", "Es ist kein Abfuhrkalender importiert.");
        }
        LocalDate today = LocalDate.now(clock);
        List<Map<String, String>> pickups = collection.pickups().stream()
                .filter(p -> !p.date().isBefore(today))
                .sorted(Comparator.comparing(WasteCollection.Pickup::date))
                .limit(MAX_PICKUPS)
                .map(p -> Map.of("art", p.type(), "datum", p.date().toString()))
                .toList();
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("zustaendig", collection.assigneeId() == null
                ? "offen: jedes Kind kann die Aufgabe übernehmen"
                : people.names().getOrDefault(collection.assigneeId(), "ehemaliges Mitglied"));
        result.put("abholungen", pickups);
        return result;
    }
}
