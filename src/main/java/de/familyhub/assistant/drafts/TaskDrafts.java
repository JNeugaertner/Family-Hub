package de.familyhub.assistant.drafts;

import static de.familyhub.permission.Action.ERSTELLEN;

import java.time.Clock;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.stereotype.Component;

import de.familyhub.assistant.llm.ToolSpec;
import de.familyhub.assistant.tools.AgentRights;
import de.familyhub.assistant.tools.People;
import de.familyhub.assistant.tools.ToolArguments;
import de.familyhub.assistant.tools.ToolException;
import de.familyhub.family.FamilyMember;
import de.familyhub.permission.Module;
import de.familyhub.permission.Permissions;
import de.familyhub.permission.Scope;
import de.familyhub.task.Task;
import de.familyhub.task.TaskAccess;
import de.familyhub.task.TaskController;
import de.familyhub.task.TaskRepository;
import de.familyhub.task.TaskStatus;
import tools.jackson.databind.json.JsonMapper;

// Entwürfe für Aufgaben: anlegen, ändern (auch abhaken), löschen. Die KI weist niemandem Aufgaben zu und vergibt
// keine Punkte (Entscheidung vom 22.10.2026): Bei neuen Aufgaben wählt die Person auf der Karte, wer sie bekommt;
// Punkte legen Eltern wie bisher auf der Aufgabenseite fest. Bestätigen (Punkte gutschreiben) geht nicht über die KI.
@Component
public class TaskDrafts extends DraftTool {

    static final String NAME = "aufgaben_entwerfen";

    private static final List<String> ACTIONS = List.of("neu", "aendern", "loeschen");
    private static final Map<String, String> PRIORITIES = ordered("niedrig", "low", "mittel", "medium", "hoch",
            "high");
    private static final Map<String, String> CATEGORIES = ordered("haushalt", "chores", "schule", "school",
            "gesundheit", "health", "besorgungen", "errands", "familie", "family", "haus_garten", "home");
    private static final Map<String, String> STATUS = ordered("offen", "todo", "in_arbeit", "inprogress",
            "erledigt", "done");
    private static final Set<String> KNOWN = Set.of("aktion", "id", "titel", "beschreibung", "faellig", "prioritaet",
            "kategorie", "status");
    private static final Set<String> CREATE_FIELDS = Set.of("title", "description", "dueDate", "priority", "category",
            "assigneeId");
    private static final Set<String> UPDATE_FIELDS = Set.of("title", "description", "dueDate", "priority", "category",
            "status");
    private static final List<String> SNAPSHOT = List.of("title", "description", "assigneeId", "dueDate", "priority",
            "category", "status");

    private final TaskRepository tasks;
    private final TaskAccess access;
    private final TaskController controller;
    private final AgentRights rights;
    private final Permissions permissions;
    private final People people;
    private final DraftSupport support;
    private final JsonMapper json;

    public TaskDrafts(DraftRepository drafts, Clock clock, TaskRepository tasks, TaskAccess access,
            TaskController controller, AgentRights rights, Permissions permissions, People people,
            DraftSupport support, JsonMapper json) {
        super(drafts, clock);
        this.tasks = tasks;
        this.access = access;
        this.controller = controller;
        this.rights = rights;
        this.permissions = permissions;
        this.people = people;
        this.support = support;
        this.json = json;
    }

    @Override
    public DraftKind kind() {
        return DraftKind.TASK;
    }

    @Override
    public ToolSpec spec() {
        Map<String, Object> entry = new LinkedHashMap<>();
        entry.put("aktion", Map.of("type", "string", "enum", ACTIONS));
        entry.put("id", Map.of("type", "string", "description", "Bei aendern und loeschen: id aus aufgaben_lesen"));
        entry.put("titel", Map.of("type", "string"));
        entry.put("beschreibung", Map.of("type", "string"));
        entry.put("faellig", Map.of("type", "string", "description", "JJJJ-MM-TT"));
        entry.put("prioritaet", Map.of("type", "string", "enum", List.copyOf(PRIORITIES.keySet())));
        entry.put("kategorie", Map.of("type", "string", "enum", List.copyOf(CATEGORIES.keySet())));
        entry.put("status", Map.of("type", "string", "enum", List.copyOf(STATUS.keySet()),
                "description", "Nur bei aendern, z. B. erledigt zum Abhaken"));
        return new ToolSpec(NAME, "Bereitet Aufgaben zum Anlegen, Ändern (auch abhaken) oder Löschen als Entwurf vor, "
                + "den die Person selbst übernimmt. Wer eine neue Aufgabe bekommt, wählt die Person auf der Karte; "
                + "Punkte vergibst du nicht.",
                Map.of("type", "object", "properties", Map.of("eintraege", Map.of("type", "array",
                        "items", Map.of("type", "object", "properties", entry, "required", List.of("aktion"),
                                "additionalProperties", false))),
                        "required", List.of("eintraege"), "additionalProperties", false));
    }

    @Override
    public String label() {
        return "Aufgaben";
    }

    @Override
    public boolean availableFor(FamilyMember viewer) {
        return rights.mayDraft(viewer, Module.AUFGABEN);
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
        if (entry.has("person") || entry.has("personen") || entry.has("punkte")) {
            throw new ToolException("Wer eine Aufgabe bekommt, wählt die Person selbst auf der Karte, und Punkte "
                    + "legen Eltern auf der Aufgabenseite fest. Lass person und punkte weg.");
        }
        entry.requireOnly(KNOWN);
        return switch (action(entry, ACTIONS)) {
            case "neu" -> create(viewer, entry);
            case "aendern" -> update(viewer, entry);
            default -> delete(viewer, entry);
        };
    }

    private Prepared create(FamilyMember viewer, ToolArguments entry) {
        if (!permissions.can(viewer, Module.AUFGABEN, ERSTELLEN, Scope.EIGEN)) {
            throw new ToolException("Du darfst keine Aufgaben anlegen.");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("title", entry.text("titel").orElseThrow(() -> new ToolException("titel fehlt.")));
        entry.text("beschreibung").ifPresent(description -> data.put("description", description));
        entry.date("faellig").ifPresent(due -> data.put("dueDate", due.toString()));
        data.put("priority", PRIORITIES.get(entry.choice("prioritaet", PRIORITIES.keySet()).orElse("mittel")));
        data.put("category", CATEGORIES.get(entry.choice("kategorie", CATEGORIES.keySet()).orElse("haushalt")));
        // Wer nur eigene Aufgaben anlegen darf (Jugendliche), legt sie für sich an; das wählt FamilyHub, nicht die KI
        boolean onlyOwn = !permissions.can(viewer, Module.AUFGABEN, ERSTELLEN, Scope.FAMILIE);
        if (onlyOwn) {
            data.put("assigneeId", viewer.id());
        }
        support.read(data, Task.class);

        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("aktion", "neu");
        summary.put("titel", data.get("title"));
        if (data.containsKey("dueDate")) {
            summary.put("faellig", data.get("dueDate"));
        } else {
            summary.put("faellig", "fehlt noch, die Person trägt es auf der Karte ein");
        }
        summary.put("person", onlyOwn ? viewer.name() : "wählt die Person auf der Karte");
        return new Prepared(DraftItem.create(data), summary);
    }

    private Prepared update(FamilyMember viewer, ToolArguments entry) {
        Task existing = find(viewer, id(entry));
        Map<String, Object> changes = new LinkedHashMap<>();
        entry.text("titel").ifPresent(title -> changes.put("title", title));
        entry.text("beschreibung").ifPresent(description -> changes.put("description", description));
        entry.date("faellig").ifPresent(due -> changes.put("dueDate", due.toString()));
        entry.choice("prioritaet", PRIORITIES.keySet()).ifPresent(p -> changes.put("priority", PRIORITIES.get(p)));
        entry.choice("kategorie", CATEGORIES.keySet()).ifPresent(c -> changes.put("category", CATEGORIES.get(c)));
        if (!changes.isEmpty()) {
            access.requireEdit(viewer, existing, support.merge(existing, changes, Task.class));
        }
        entry.choice("status", STATUS.keySet()).ifPresent(status -> {
            access.requireStatusChange(viewer, existing);
            changes.put("status", STATUS.get(status));
        });
        if (changes.isEmpty()) {
            throw new ToolException("Nichts zu ändern angegeben.");
        }
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("aktion", "aendern");
        summary.put("titel", existing.title());
        summary.put("person", personOf(existing));
        return new Prepared(DraftItem.update(existing.id(), changes, support.snapshot(existing, SNAPSHOT)), summary);
    }

    private Prepared delete(FamilyMember viewer, ToolArguments entry) {
        Task existing = find(viewer, id(entry));
        access.requireDelete(viewer, existing);
        Map<String, Object> summary = new LinkedHashMap<>();
        summary.put("aktion", "loeschen");
        summary.put("titel", existing.title());
        summary.put("person", personOf(existing));
        return new Prepared(DraftItem.delete(existing.id(), support.snapshot(existing, SNAPSHOT)), summary);
    }

    @Override
    public String apply(FamilyMember viewer, DraftItem item, Map<String, Object> data) {
        switch (item.action()) {
            case CREATE -> {
                Map<String, Object> task = new LinkedHashMap<>(data);
                task.put("points", 0);
                Task saved = controller.create(support.read(task, Task.class)).getBody();
                support.markViaAssistant(saved.id(), Task.class);
            }
            case UPDATE -> {
                Map<String, Object> content = new LinkedHashMap<>(data);
                Object status = content.remove("status");
                if (!content.isEmpty()) {
                    controller.update(item.targetId(),
                            support.merge(controller.get(item.targetId()), content, Task.class));
                }
                if (status != null) {
                    controller.changeStatus(item.targetId(),
                            new TaskController.StatusChange(json.convertValue(status, TaskStatus.class)));
                }
            }
            case DELETE -> controller.delete(item.targetId());
        }
        return outcome(false);
    }

    private Task find(FamilyMember viewer, String id) {
        return tasks.findById(id).filter(t -> access.canSee(viewer, t))
                .orElseThrow(() -> new ToolException("Eine Aufgabe mit der id " + id + " gibt es nicht."));
    }

    private String personOf(Task task) {
        return task.assigneeId() == null ? "noch niemand (Bonus-Aufgabe)"
                : people.names().getOrDefault(task.assigneeId(), "ehemaliges Mitglied");
    }
}
