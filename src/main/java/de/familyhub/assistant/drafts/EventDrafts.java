package de.familyhub.assistant.drafts;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

import org.springframework.stereotype.Component;

import de.familyhub.assistant.AssistantArea;
import de.familyhub.assistant.insights.ConflictFinder;
import de.familyhub.assistant.llm.ToolSpec;
import de.familyhub.assistant.tools.AgentRights;
import de.familyhub.assistant.tools.People;
import de.familyhub.assistant.tools.ToolArguments;
import de.familyhub.assistant.tools.ToolException;
import de.familyhub.calendar.CalendarAccess;
import de.familyhub.calendar.CalendarEvent;
import de.familyhub.calendar.CalendarEventController;
import de.familyhub.calendar.CalendarEventRepository;
import de.familyhub.calendar.EventStatus;
import de.familyhub.family.FamilyMember;
import de.familyhub.permission.Module;

// Entwürfe für Termine: anlegen, ändern, löschen. Geprüft mit CalendarAccess, übernommen über CalendarEventController
// (wer für andere nur vorschlagen darf, macht also auch hier einen Vorschlag).
@Component
public class EventDrafts extends DraftTool {

    static final String NAME = "kalender_entwerfen";

    private static final List<String> ACTIONS = List.of("neu", "aendern", "loeschen");
    private static final List<String> CATEGORIES = List.of("family", "school", "sports", "appointment", "work",
            "reminder");
    private static final Set<String> KNOWN = Set.of("aktion", "id", "titel", "beginn", "ende", "ganztaegig",
            "personen", "kategorie", "ort", "beschreibung", "privat");
    private static final Set<String> FIELDS = Set.of("title", "start", "end", "memberIds", "category", "location",
            "description", "private");
    private static final List<String> SNAPSHOT = List.of("title", "start", "end", "memberIds", "category", "location",
            "description", "private");

    private final CalendarEventRepository events;
    private final CalendarAccess access;
    private final CalendarEventController controller;
    private final AgentRights rights;
    private final People people;
    private final DraftSupport support;

    public EventDrafts(DraftRepository drafts, Clock clock, CalendarEventRepository events, CalendarAccess access,
            CalendarEventController controller, AgentRights rights, People people, DraftSupport support) {
        super(drafts, clock);
        this.events = events;
        this.access = access;
        this.controller = controller;
        this.rights = rights;
        this.people = people;
        this.support = support;
    }

    @Override
    public DraftKind kind() {
        return DraftKind.EVENT;
    }

    @Override
    public ToolSpec spec() {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("aktion", Map.of("type", "string", "enum", ACTIONS));
        entry.put("id", Map.of("type", "string", "description", "Bei aendern und loeschen: id aus kalender_lesen"));
        entry.put("titel", Map.of("type", "string"));
        entry.put("beginn", Map.of("type", "string",
                "description", "JJJJ-MM-TTTHH:MM; bei ganztaegig nur JJJJ-MM-TT"));
        entry.put("ende", Map.of("type", "string", "description",
                "Wie beginn; ohne Angabe 1 Stunde nach Beginn, bei ganztaegig der letzte Tag (einschließlich)"));
        entry.put("ganztaegig", Map.of("type", "boolean"));
        entry.put("personen", Map.of("type", "array", "items", Map.of("type", "string"),
                "description", "Namen oder „ich“; bei neu ohne Angabe die fragende Person"));
        entry.put("kategorie", Map.of("type", "string", "enum", CATEGORIES, "description", "Standard family"));
        entry.put("ort", Map.of("type", "string"));
        entry.put("beschreibung", Map.of("type", "string", "description", "Nur, wenn die Person mehr als den Titel nennt"));
        entry.put("privat", Map.of("type", "boolean"));
        return new ToolSpec(NAME, "Bereitet Termine zum Anlegen, Ändern oder Löschen als Entwurf vor, den die Person "
                + "selbst übernimmt. Mehrere Termine in einen Aufruf. Bei aendern nur die Angaben, die sich ändern.",
                Map.of("type", "object", "properties", Map.of("eintraege", Map.of("type", "array",
                        "items", Map.of("type", "object", "properties", entry, "required", List.of("aktion"),
                                "additionalProperties", false))),
                        "required", List.of("eintraege"), "additionalProperties", false));
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
        return rights.mayDraft(viewer, Module.KALENDER);
    }

    @Override
    public Set<String> editable(DraftItem.Action action) {
        return action == DraftItem.Action.DELETE ? Set.of() : FIELDS;
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
        data.put("title", entry.text("titel").orElseThrow(() -> new ToolException("titel fehlt.")));
        Times times = times(entry, null);
        data.put("start", DraftSupport.dateTime(times.start()));
        data.put("end", DraftSupport.dateTime(times.end()));
        data.put("memberIds", memberIds(viewer, entry).orElse(List.of(viewer.id())));
        data.put("category", entry.choice("kategorie", CATEGORIES).orElse("family"));
        entry.text("ort").ifPresent(location -> data.put("location", location));
        // Modelle wiederholen gern den Titel als Beschreibung; das wäre auf der Karte nur doppelt
        entry.text("beschreibung").filter(description -> !description.equalsIgnoreCase(data.get("title").toString()))
                .ifPresent(description -> data.put("description", description));
        data.put("private", entry.flag("privat", false));

        CalendarEvent event = support.read(data, CalendarEvent.class);
        EventStatus status = access.statusForNewEvent(viewer, event);
        Map<String, Object> summary = summary("neu", event);
        if (status == EventStatus.PROPOSED) {
            summary.put("wird", "ein Vorschlag, den ein Elternteil freigeben muss");
        }
        overlaps(viewer, event, null).ifPresent(titles -> summary.put("ueberschneidet_sich_mit", titles));
        return new Prepared(DraftItem.create(data), summary);
    }

    private Prepared update(FamilyMember viewer, ToolArguments entry) {
        CalendarEvent existing = find(viewer, id(entry));
        Map<String, Object> changes = new LinkedHashMap<>();
        entry.text("titel").ifPresent(title -> changes.put("title", title));
        if (entry.has("beginn") || entry.has("ende") || entry.has("ganztaegig")) {
            Times times = times(entry, existing);
            changes.put("start", DraftSupport.dateTime(times.start()));
            changes.put("end", DraftSupport.dateTime(times.end()));
        }
        memberIds(viewer, entry).ifPresent(ids -> changes.put("memberIds", ids));
        entry.choice("kategorie", CATEGORIES).ifPresent(category -> changes.put("category", category));
        entry.text("ort").ifPresent(location -> changes.put("location", location));
        entry.text("beschreibung").ifPresent(description -> changes.put("description", description));
        entry.optionalFlag("privat").ifPresent(privateEvent -> changes.put("private", privateEvent));
        if (changes.isEmpty()) {
            throw new ToolException("Nichts zu ändern angegeben.");
        }
        CalendarEvent changed = support.merge(existing, changes, CalendarEvent.class);
        access.requireUpdate(viewer, existing, changed);
        Map<String, Object> summary = summary("aendern", changed);
        overlaps(viewer, changed, existing.id()).ifPresent(titles -> summary.put("ueberschneidet_sich_mit", titles));
        return new Prepared(DraftItem.update(existing.id(), changes, support.snapshot(existing, SNAPSHOT)), summary);
    }

    private Prepared delete(FamilyMember viewer, ToolArguments entry) {
        CalendarEvent existing = find(viewer, id(entry));
        access.requireDelete(viewer, existing);
        return new Prepared(DraftItem.delete(existing.id(), support.snapshot(existing, SNAPSHOT)),
                summary("loeschen", existing));
    }

    @Override
    public String apply(FamilyMember viewer, DraftItem item, Map<String, Object> data) {
        return switch (item.action()) {
            case CREATE -> {
                CalendarEvent saved = controller.create(support.read(data, CalendarEvent.class)).getBody();
                support.markViaAssistant(saved.id(), CalendarEvent.class);
                yield outcome(saved.isProposal());
            }
            case UPDATE -> {
                CalendarEvent current = controller.get(item.targetId());
                yield outcome(controller.update(item.targetId(),
                        support.merge(current, data, CalendarEvent.class)).isProposal());
            }
            case DELETE -> {
                controller.delete(item.targetId());
                yield outcome(false);
            }
        };
    }

    // Termine, die die Person nicht sehen darf, gibt es für den Assistenten nicht
    private CalendarEvent find(FamilyMember viewer, String id) {
        CalendarEvent event = events.findById(id).filter(e -> access.canSee(viewer, e))
                .orElseThrow(() -> new ToolException("Einen Termin mit der id " + id + " gibt es nicht."));
        if (event.isExternal()) {
            throw new ToolException("„" + event.title() + "“ kommt aus Google und wird nur dort geändert.");
        }
        return event;
    }

    private Optional<List<String>> memberIds(FamilyMember viewer, ToolArguments entry) {
        return entry.texts("personen").map(names -> {
            List<String> ids = new ArrayList<>();
            names.forEach(name -> ids.add(people.resolve(viewer, Optional.of(name)).orElseThrow().id()));
            return ids;
        });
    }

    private record Times(LocalDateTime start, LocalDateTime end) {
    }

    // Beginn und Ende aus den Argumenten; beim Ändern bleibt, was nicht angegeben ist (auch die Dauer)
    private static Times times(ToolArguments entry, CalendarEvent existing) {
        boolean wasAllDay = existing != null && ConflictFinder.isAllDay(existing);
        if (entry.optionalFlag("ganztaegig").orElse(wasAllDay)) {
            LocalDate first = entry.date("beginn").orElse(existing == null ? null : existing.start().toLocalDate());
            if (first == null) {
                throw new ToolException("beginn fehlt (JJJJ-MM-TT).");
            }
            LocalDate last = entry.date("ende").orElse(wasAllDay && !entry.has("beginn")
                    ? existing.end().toLocalDate().minusDays(1) : first);
            return new Times(first.atStartOfDay(), last.plusDays(1).atStartOfDay());
        }
        LocalDateTime start = entry.dateTime("beginn").orElse(existing == null ? null : existing.start());
        if (start == null) {
            throw new ToolException("beginn fehlt (JJJJ-MM-TTTHH:MM).");
        }
        Duration length = existing == null || wasAllDay ? Duration.ofHours(1)
                : Duration.between(existing.start(), existing.end());
        return new Times(start, entry.dateTime("ende").orElse(start.plus(length)));
    }

    // Andere Termine derselben Personen zur selben Zeit, die die Person sieht; ganztägige zählen nicht
    private Optional<List<String>> overlaps(FamilyMember viewer, CalendarEvent event, String self) {
        if (ConflictFinder.isAllDay(event)) {
            return Optional.empty();
        }
        Predicate<CalendarEvent> visible = access.visibilityFor(viewer);
        List<String> titles = events.findOverlapping(event.start(), event.end()).stream()
                .filter(visible)
                .filter(other -> !other.id().equals(self) && !ConflictFinder.isAllDay(other))
                .filter(other -> other.memberIds().stream().anyMatch(event.memberIds()::contains))
                .map(other -> other.title() + " (" + other.start().toLocalTime() + "–" + other.end().toLocalTime()
                        + ")")
                .toList();
        return titles.isEmpty() ? Optional.empty() : Optional.of(titles);
    }

    private Map<String, Object> summary(String action, CalendarEvent event) {
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("aktion", action);
        summary.put("titel", event.title());
        summary.put("beginn", event.start().toString());
        summary.put("ende", event.end().toString());
        summary.put("personen", People.namesOf(event.memberIds(), people.names()));
        return summary;
    }
}
