package de.familyhub.task;

import java.net.URI;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.function.Predicate;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.FindAndModifyOptions;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import de.familyhub.achievements.AchievementService;
import de.familyhub.family.FamilyMember;
import de.familyhub.family.FamilyMemberRepository;
import de.familyhub.points.PointsService;
import de.familyhub.security.CurrentMember;
import de.familyhub.web.ApiException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@RestController
@RequestMapping("/api/tasks")
@Tag(name = "Aufgaben")
public class TaskController {

    private final TaskRepository tasks;
    private final FamilyMemberRepository members;
    private final CurrentMember currentMember;
    private final TaskAccess access;
    private final PointsService points;
    private final AchievementService achievements;
    private final MongoTemplate mongo;
    private final Clock clock;

    public TaskController(TaskRepository tasks, FamilyMemberRepository members, CurrentMember currentMember,
            TaskAccess access, PointsService points, AchievementService achievements, MongoTemplate mongo,
            Clock clock) {
        this.tasks = tasks;
        this.members = members;
        this.currentMember = currentMember;
        this.access = access;
        this.points = points;
        this.achievements = achievements;
        this.mongo = mongo;
        this.clock = clock;
    }

    public record StatusChange(
            @NotNull(message = "Status ist Pflicht")
            TaskStatus status) {
    }

    @GetMapping
    @Operation(summary = "Aufgaben abfragen",
            description = "Nach Fälligkeit sortiert. Enthält nur Aufgaben, die die angemeldete Person sehen darf.")
    public List<Task> list(
            @Parameter(description = "Nur Aufgaben dieses Familienmitglieds")
            @RequestParam(required = false) String assigneeId,
            @Parameter(description = "Nur Aufgaben mit diesem Status, z. B. done für wartende Bestätigungen")
            @RequestParam(required = false) TaskStatus status) {
        List<Task> result = assigneeId == null
                ? tasks.findAll(Sort.by("dueDate", "title"))
                : tasks.findByAssigneeIdOrderByDueDateAsc(assigneeId);
        Predicate<Task> visible = access.visibilityFor(currentMember.get());
        return result.stream()
                .filter(visible)
                .filter(task -> status == null || task.status() == status)
                .toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Eine Aufgabe")
    public Task get(@PathVariable String id) {
        return findVisible(id, currentMember.get());
    }

    @PostMapping
    @Operation(summary = "Aufgabe anlegen",
            description = "Neue Aufgaben sind offen (todo). Eine mitgeschickte id, status, createdBy und Bestätigung "
                    + "werden ignoriert. Punkte über 0 dürfen nur Administratoren festlegen. Bonus-Aufgaben (bonus) "
                    + "legen nur Administratoren an: ohne Person, mit Punkten, Frist optional.")
    public ResponseEntity<Task> create(@Valid @RequestBody Task task) {
        FamilyMember viewer = currentMember.get();
        access.requireCreate(viewer, task);
        Task toSave = copy(null, task, task.bonus() ? null : task.assigneeId(), TaskStatus.TODO, viewer.id(),
                task.bonus());
        requireAssignment(toSave);
        Task saved = tasks.save(toSave);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(saved.id()).toUri();
        return ResponseEntity.created(location).body(saved);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Aufgabe ändern",
            description = "Ändert den Inhalt; Status, createdBy und Bestätigung bleiben. Bestätigte Aufgaben sind gesperrt.")
    public Task update(@PathVariable String id, @Valid @RequestBody Task task) {
        FamilyMember viewer = currentMember.get();
        Task existing = findVisible(id, viewer);
        // Ob Bonus-Aufgabe, bleibt; bei Bonus-Aufgaben bleibt auch, wer sie übernommen hat (claim/release)
        Task changed = existing.bonus()
                ? copy(id, task, existing.assigneeId(), existing.status(), existing.createdBy(), true)
                : copy(id, task, task.assigneeId(), existing.status(), existing.createdBy(), false);
        access.requireEdit(viewer, existing, changed);
        requireAssignment(changed);
        return tasks.save(changed);
    }

    @PostMapping("/{id}/claim")
    @Operation(summary = "Bonus-Aufgabe übernehmen",
            description = "Für Kinder und Jugendliche. Die Aufgabe gehört danach der Person (in Arbeit) und ist für "
                    + "andere nicht mehr offen. Wer zuerst kommt, bekommt sie.")
    public Task claim(@PathVariable String id) {
        FamilyMember viewer = currentMember.get();
        Task task = findVisible(id, viewer);
        access.requireClaim(viewer, task);
        // Nur übernehmen, wenn sie in diesem Moment noch frei ist (zwei Kinder gleichzeitig)
        Task claimed = mongo.findAndModify(
                Query.query(Criteria.where("_id").is(id).and("assigneeId").is(null).and("status").is(TaskStatus.TODO)),
                Update.update("assigneeId", viewer.id()).set("status", TaskStatus.IN_PROGRESS),
                FindAndModifyOptions.options().returnNew(true), Task.class);
        if (claimed == null) {
            throw ApiException.conflict("Diese Bonus-Aufgabe hat schon jemand übernommen.");
        }
        return claimed;
    }

    @PostMapping("/{id}/release")
    @Operation(summary = "Bonus-Aufgabe zurückgeben",
            description = "Wer sie übernommen hat (oder ein Administrator), solange sie nicht erledigt ist. Sie ist "
                    + "danach wieder für alle offen.")
    public Task release(@PathVariable String id) {
        FamilyMember viewer = currentMember.get();
        Task task = findVisible(id, viewer);
        access.requireRelease(viewer, task);
        return tasks.save(task.withAssignee(null, TaskStatus.TODO));
    }

    @PatchMapping("/{id}/status")
    @Operation(summary = "Status ändern (abhaken)",
            description = "todo, inprogress oder done. Bestätigen geht über POST /api/tasks/{id}/confirm.")
    public Task changeStatus(@PathVariable String id, @Valid @RequestBody StatusChange change) {
        if (change.status() == TaskStatus.CONFIRMED) {
            throw ApiException.invalidField("status", "Bestätigen darf nur ein Administrator über „Bestätigen“");
        }
        FamilyMember viewer = currentMember.get();
        Task existing = findVisible(id, viewer);
        access.requireStatusChange(viewer, existing);
        return tasks.save(existing.withStatus(change.status()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Aufgabe löschen", description = "Gutgeschriebene Punkte bleiben in der Historie erhalten.")
    public void delete(@PathVariable String id) {
        FamilyMember viewer = currentMember.get();
        Task existing = findVisible(id, viewer);
        access.requireDelete(viewer, existing);
        tasks.deleteById(id);
    }

    public record DeletedTasks(int deleted) {
    }

    @DeleteMapping("/completed")
    @Operation(summary = "Abgeschlossene Aufgaben löschen",
            description = "Nur für Administratoren. Löscht bestätigte Aufgaben und erledigte ohne Punkte. Erledigte "
                    + "Aufgaben, die noch auf Bestätigung warten, bleiben stehen. Die Punkte-Historie bleibt erhalten.")
    public DeletedTasks deleteCompleted() {
        access.requireDeleteAll(currentMember.get());
        List<Task> completed = tasks.findAll().stream().filter(Task::isCompleted).toList();
        tasks.deleteAll(completed);
        return new DeletedTasks(completed.size());
    }

    @PostMapping("/{id}/confirm")
    @Operation(summary = "Erledigte Aufgabe bestätigen",
            description = "Nur für Administratoren. Schreibt die Punkte einmalig gut; die Aufgabe ist danach "
                    + "abgeschlossen (confirmed). Zählt für die Erfolge des Kindes. Wiederkehrende Bonus-Aufgaben sind danach als "
                    + "neue Aufgabe wieder offen.")
    public Task confirm(@PathVariable String id) {
        FamilyMember viewer = currentMember.get();
        access.requireConfirmationRight(viewer);
        Task task = findAwaitingConfirmation(id, viewer);
        points.awardForTask(task, viewer);
        Task confirmed = tasks.save(task.confirmed(LocalDateTime.now(clock), viewer.id()));
        achievements.onTaskConfirmed(confirmed, viewer);
        if (confirmed.repeatable()) {
            tasks.save(confirmed.nextRound());
        }
        return confirmed;
    }

    @PostMapping("/{id}/reopen")
    @Operation(summary = "Erledigte Aufgabe zurückgeben",
            description = "Nur für Administratoren. Die Aufgabe ist wieder in Arbeit, es gibt keine Punkte.")
    public Task reopen(@PathVariable String id) {
        FamilyMember viewer = currentMember.get();
        access.requireConfirmationRight(viewer);
        return tasks.save(findAwaitingConfirmation(id, viewer).withStatus(TaskStatus.IN_PROGRESS));
    }

    private Task findAwaitingConfirmation(String id, FamilyMember viewer) {
        Task task = findVisible(id, viewer);
        if (!task.isAwaitingConfirmation()) {
            throw ApiException.conflict(task.status() == TaskStatus.CONFIRMED
                    ? "Die Aufgabe ist bereits bestätigt, die Punkte sind gutgeschrieben."
                    : "Nur erledigte Aufgaben mit Punkten warten auf eine Bestätigung.");
        }
        return task;
    }

    // Aufgaben, die jemand nicht sehen darf, gelten für ihn als nicht vorhanden.
    private Task findVisible(String id, FamilyMember viewer) {
        return tasks.findById(id)
                .filter(task -> access.canSee(viewer, task))
                .orElseThrow(() -> ApiException.notFound("Aufgabe " + id + " existiert nicht."));
    }

    // Normale Aufgaben brauchen Person und Frist, Bonus-Aufgaben Punkte
    private void requireAssignment(Task task) {
        if (task.bonus()) {
            if (task.points() <= 0) {
                throw ApiException.invalidField("points", "Bonus-Aufgaben brauchen Punkte");
            }
        } else {
            if (task.assigneeId() == null) {
                throw ApiException.invalidField("assigneeId", "Aufgabe muss einem Familienmitglied zugewiesen sein");
            }
            if (task.dueDate() == null) {
                throw ApiException.invalidField("dueDate", "Fälligkeit ist Pflicht");
            }
        }
        if (task.assigneeId() != null && !members.existsById(task.assigneeId())) {
            throw ApiException.invalidField("assigneeId", "Familienmitglied existiert nicht");
        }
    }

    private static Task copy(String id, Task t, String assigneeId, TaskStatus status, String createdBy,
            boolean bonus) {
        return new Task(id, t.title(), t.description(), assigneeId, t.dueDate(), t.priority(), t.category(),
                t.points(), status, createdBy, null, null, bonus, t.repeatable());
    }
}
