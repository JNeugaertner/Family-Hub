package de.familyhub.assistant.tools;

import java.time.Clock;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import org.springframework.stereotype.Component;

import de.familyhub.assistant.AssistantArea;
import de.familyhub.assistant.insights.ConflictFinder;
import de.familyhub.assistant.llm.ToolSpec;
import de.familyhub.calendar.CalendarAccess;
import de.familyhub.calendar.CalendarEvent;
import de.familyhub.calendar.CalendarEventRepository;
import de.familyhub.family.FamilyMember;
import de.familyhub.permission.Module;
import de.familyhub.permission.Scope;

// Termine aus dem Familienkalender, genau die, die die fragende Person auch im Kalender sieht
@Component
public class CalendarTool implements AssistantTool {

    static final String NAME = "kalender_lesen";
    static final int MAX_DAYS = 62;
    static final int MAX_EVENTS = 60;

    private final CalendarEventRepository events;
    private final CalendarAccess access;
    private final AgentRights rights;
    private final People people;
    private final Clock clock;

    public CalendarTool(CalendarEventRepository events, CalendarAccess access, AgentRights rights, People people,
            Clock clock) {
        this.events = events;
        this.access = access;
        this.rights = rights;
        this.people = people;
        this.clock = clock;
    }

    @Override
    public ToolSpec spec() {
        Map<String, Object> properties = new HashMap<>(DateRange.schemaProperties());
        properties.put("person", Map.of("type", "string",
                "description", "Nur Termine dieser Person: ihr Name oder „ich“; ohne Angabe alle"));
        return new ToolSpec(NAME, "Liest Termine aus dem Familienkalender in einem Zeitraum. Ohne Angaben: heute und "
                + "die nächsten 6 Tage. Höchstens " + MAX_DAYS + " Tage auf einmal. Vorschläge (vorschlag: true) "
                + "sind noch nicht freigegeben.",
                Map.of("type", "object", "properties", properties, "additionalProperties", false));
    }

    @Override
    public AssistantArea area() {
        return AssistantArea.KALENDER;
    }

    @Override
    public String label() {
        return "Kalender";
    }

    @Override
    public boolean availableFor(FamilyMember viewer) {
        return rights.mayRead(viewer, Module.KALENDER, Scope.EIGEN);
    }

    @Override
    public Object run(FamilyMember viewer, ToolArguments args) {
        DateRange range = DateRange.of(args, LocalDate.now(clock), 7, MAX_DAYS);
        Optional<FamilyMember> person = people.resolve(viewer, args.text("person"));
        List<CalendarEvent> found = person
                .map(p -> events.findOverlappingForMember(p.id(), range.start(), range.end()))
                .orElseGet(() -> events.findOverlapping(range.start(), range.end()));
        Predicate<CalendarEvent> visible = access.visibilityFor(viewer);
        Map<String, String> names = people.names();
        List<Map<String, Object>> rows = found.stream().filter(visible).limit(MAX_EVENTS + 1L)
                .map(e -> row(e, names)).toList();

        Map<String, Object> result = new LinkedHashMap<>();
        result.put("zeitraum", range.describe());
        result.put("termine", rows.size() > MAX_EVENTS ? rows.subList(0, MAX_EVENTS) : rows);
        if (rows.size() > MAX_EVENTS) {
            result.put("gekuerzt", "Es gibt mehr Termine; bitte einen kürzeren Zeitraum wählen.");
        }
        return result;
    }

    private static Map<String, Object> row(CalendarEvent e, Map<String, String> names) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", e.id());
        row.put("titel", e.title());
        if (ConflictFinder.isAllDay(e)) {
            row.put("ganztaegig_von", e.start().toLocalDate().toString());
            row.put("ganztaegig_bis", e.end().toLocalDate().minusDays(1).toString());
        } else {
            row.put("beginn", e.start().toString());
            row.put("ende", e.end().toString());
        }
        row.put("personen", People.namesOf(e.memberIds(), names));
        row.put("kategorie", e.category().name().toLowerCase());
        if (e.location() != null && !e.location().isBlank()) {
            row.put("ort", e.location());
        }
        if (e.description() != null && !e.description().isBlank()) {
            row.put("beschreibung", e.description());
        }
        if (e.privateEvent()) {
            row.put("privat", true);
        }
        if (e.isProposal()) {
            row.put("vorschlag", true);
        }
        if (e.isExternal()) {
            row.put("aus_google", true);
        }
        return row;
    }
}
