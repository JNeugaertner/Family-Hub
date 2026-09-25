package de.familyhub.task;

import java.net.URI;
import java.util.List;
import java.util.function.Predicate;

import org.springframework.data.domain.Sort;
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

import de.familyhub.family.FamilyMember;
import de.familyhub.family.FamilyMemberRepository;
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

    public TaskController(TaskRepository tasks, FamilyMemberRepository members, CurrentMember currentMember,
            TaskAccess access) {
        this.tasks = tasks;
        this.members = members;
        this.currentMember = currentMember;
        this.access = access;
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
                    + "werden ignoriert. Punkte über 0 dürfen nur Administratoren festlegen.")
    public ResponseEntity<Task> create(@Valid @RequestBody Task task) {
        FamilyMember viewer = currentMember.get();
        access.requireCreate(viewer, task);
        requireMember(task.assigneeId());
        Task saved = tasks.save(copy(null, task, TaskStatus.TODO, viewer.id()));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(saved.id()).toUri();
        return ResponseEntity.created(location).body(saved);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Aufgabe ändern",
            description = "Ändert den Inhalt; Status, createdBy und Bestätigung bleiben. Bestätigte Aufgaben sind gesperrt.")
    public Task update(@PathVariable String id, @Valid @RequestBody Task task) {
        FamilyMember viewer = currentMember.get();
        Task existing = findVisible(id, viewer);
        access.requireEdit(viewer, existing, task);
        requireMember(task.assigneeId());
        return tasks.save(copy(id, task, existing.status(), existing.createdBy()));
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

    // Aufgaben, die jemand nicht sehen darf, gelten für ihn als nicht vorhanden.
    private Task findVisible(String id, FamilyMember viewer) {
        return tasks.findById(id)
                .filter(task -> access.canSee(viewer, task))
                .orElseThrow(() -> ApiException.notFound("Aufgabe " + id + " existiert nicht."));
    }

    private void requireMember(String memberId) {
        if (!members.existsById(memberId)) {
            throw ApiException.invalidField("assigneeId", "Familienmitglied existiert nicht");
        }
    }

    private static Task copy(String id, Task t, TaskStatus status, String createdBy) {
        return new Task(id, t.title(), t.description(), t.assigneeId(), t.dueDate(), t.priority(), t.category(),
                t.points(), status, createdBy, null, null);
    }
}
