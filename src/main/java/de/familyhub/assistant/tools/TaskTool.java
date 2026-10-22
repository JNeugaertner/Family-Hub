package de.familyhub.assistant.tools;

import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Predicate;

import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Component;

import de.familyhub.assistant.AssistantArea;
import de.familyhub.assistant.llm.ToolSpec;
import de.familyhub.family.FamilyMember;
import de.familyhub.permission.Module;
import de.familyhub.permission.Scope;
import de.familyhub.task.Task;
import de.familyhub.task.TaskAccess;
import de.familyhub.task.TaskRepository;
import de.familyhub.task.TaskStatus;

// Aufgaben, genau die, die die fragende Person auch auf der Aufgabenseite sieht
@Component
public class TaskTool implements AssistantTool {

    static final String NAME = "aufgaben_lesen";
    static final int MAX_TASKS = 60;

    private final TaskRepository tasks;
    private final TaskAccess access;
    private final AgentRights rights;
    private final People people;
    private final Clock clock;

    public TaskTool(TaskRepository tasks, TaskAccess access, AgentRights rights, People people, Clock clock) {
        this.tasks = tasks;
        this.access = access;
        this.rights = rights;
        this.people = people;
        this.clock = clock;
    }

    @Override
    public ToolSpec spec() {
        return new ToolSpec(NAME, "Liest Aufgaben, nach Fälligkeit sortiert. Ohne Angaben: alle noch offenen. "
                + "Bonus-Aufgaben ohne Person kann jedes Kind übernehmen.",
                Map.of("type", "object", "properties", Map.of(
                        "person", Map.of("type", "string",
                                "description", "Nur Aufgaben dieser Person: ihr Name oder „ich“; ohne Angabe alle"),
                        "status", Map.of("type", "string", "enum", List.of("offen", "erledigt", "alle"),
                                "description", "offen (Standard): noch nicht erledigt; erledigt: erledigt oder "
                                        + "bestätigt; alle")),
                        "additionalProperties", false));
    }

    @Override
    public AssistantArea area() {
        return AssistantArea.AUFGABEN;
    }

    @Override
    public String label() {
        return "Aufgaben";
    }

    @Override
    public boolean availableFor(FamilyMember viewer) {
        return rights.mayRead(viewer, Module.AUFGABEN, Scope.EIGEN);
    }

    @Override
    public Object run(FamilyMember viewer, ToolArguments args) {
        Optional<FamilyMember> person = people.resolve(viewer, args.text("person"));
        String status = args.text("status").orElse("offen");
        Predicate<Task> wanted = switch (status) {
            case "offen" -> t -> t.status() == TaskStatus.TODO || t.status() == TaskStatus.IN_PROGRESS;
            case "erledigt" -> t -> t.status() == TaskStatus.DONE || t.status() == TaskStatus.CONFIRMED;
            case "alle" -> t -> true;
            default -> throw new ToolException("status muss offen, erledigt oder alle sein.");
        };
        Predicate<Task> visible = access.visibilityFor(viewer);
        Map<String, String> names = people.names();
        LocalDate today = LocalDate.now(clock);
        List<Map<String, Object>> rows = tasks.findAll(Sort.by("dueDate", "title")).stream()
                .filter(visible)
                .filter(wanted)
                .filter(t -> person.isEmpty() || person.get().id().equals(t.assigneeId()))
                .limit(MAX_TASKS)
                .map(t -> row(t, names, today))
                .toList();
        return Map.of("aufgaben", rows);
    }

    private static Map<String, Object> row(Task t, Map<String, String> names, LocalDate today) {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", t.id());
        row.put("titel", t.title());
        row.put("person", t.assigneeId() == null ? "noch niemand (Bonus-Aufgabe)"
                : names.getOrDefault(t.assigneeId(), "ehemaliges Mitglied"));
        if (t.dueDate() != null) {
            row.put("faellig", t.dueDate().toString());
            boolean open = t.status() == TaskStatus.TODO || t.status() == TaskStatus.IN_PROGRESS;
            if (open && t.dueDate().isBefore(today)) {
                row.put("ueberfaellig", true);
            }
        }
        row.put("prioritaet", switch (t.priority()) {
            case HIGH -> "hoch";
            case MEDIUM -> "mittel";
            case LOW -> "niedrig";
        });
        row.put("status", switch (t.status()) {
            case TODO -> "offen";
            case IN_PROGRESS -> "in Arbeit";
            case DONE -> t.isAwaitingConfirmation() ? "erledigt, wartet auf Bestätigung" : "erledigt";
            case CONFIRMED -> "bestätigt";
        });
        if (t.points() > 0) {
            row.put("punkte", t.points());
        }
        if (t.bonus()) {
            row.put("bonus", true);
        }
        if (t.description() != null && !t.description().isBlank()) {
            row.put("beschreibung", t.description());
        }
        return row;
    }
}
