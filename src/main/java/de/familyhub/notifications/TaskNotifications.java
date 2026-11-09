package de.familyhub.notifications;

import static de.familyhub.notifications.NotificationTexts.day;
import static de.familyhub.notifications.NotificationTexts.quoted;

import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import de.familyhub.family.FamilyMember;
import de.familyhub.family.FamilyMemberRepository;
import de.familyhub.task.Task;
import de.familyhub.task.TaskAccess;
import de.familyhub.task.TaskPriority;
import de.familyhub.task.TaskRepository;
import de.familyhub.task.TaskStatus;

// Dringende Aufgaben (Entscheidung vom 21.10.2026). Dringend ist, was auch das Dashboard so nennt: zugewiesen, noch
// offen, Priorität hoch. Die zuständige Person erfährt es sofort, wenn ihr eine solche Aufgabe zugewiesen oder eine
// ihrer Aufgaben auf "hoch" gestellt wird, und morgens am Fälligkeitstag, falls sie dann noch offen ist.
// Wer die Änderung selbst gemacht hat, bekommt keine Nachricht.
@Component
public class TaskNotifications {

    private final Notifications notifications;
    private final TaskRepository tasks;
    private final FamilyMemberRepository members;
    private final TaskAccess access;
    private final Clock clock;

    public TaskNotifications(Notifications notifications, TaskRepository tasks, FamilyMemberRepository members,
            TaskAccess access, Clock clock) {
        this.notifications = notifications;
        this.tasks = tasks;
        this.members = members;
        this.access = access;
        this.clock = clock;
    }

    // before: Stand vor dem Speichern, null bei neuen Aufgaben; actor: null bei automatisch angelegten (Müllabfuhr)
    public void saved(Task before, Task after, FamilyMember actor) {
        if (!isUrgent(after) || (actor != null && actor.id().equals(after.assigneeId()))) {
            return;
        }
        boolean newlyUrgent = before == null || !after.assigneeId().equals(before.assigneeId())
                || before.priority() != TaskPriority.HIGH;
        if (!newlyUrgent) {
            return;
        }
        String due = after.dueDate() == null ? "" : ", fällig " + day(after.dueDate(), LocalDate.now(clock));
        members.findById(after.assigneeId())
                .filter(assignee -> access.canSee(assignee, after))
                .ifPresent(assignee -> notifications.send(assignee, NotificationKind.TASKS,
                        "⚠️ Dringende Aufgabe für dich: " + quoted(after.title()) + due));
    }

    // Eine Nachricht je Person mit allen dringenden Aufgaben, die heute fällig sind
    @Scheduled(cron = "${familyhub.notifications.due-reminder-cron:0 0 7 * * *}")
    public void remindDueToday() {
        Map<String, List<Task>> dueByAssignee = tasks
                .findByDueDateAndPriority(LocalDate.now(clock), TaskPriority.HIGH).stream()
                .filter(TaskNotifications::isUrgent)
                .collect(Collectors.groupingBy(Task::assigneeId, LinkedHashMap::new, Collectors.toList()));
        dueByAssignee.forEach((assigneeId, due) -> members.findById(assigneeId).ifPresent(assignee -> {
            List<String> titles = due.stream()
                    .filter(task -> access.canSee(assignee, task))
                    .map(task -> quoted(task.title()))
                    .toList();
            if (!titles.isEmpty()) {
                notifications.send(assignee, NotificationKind.TASKS,
                        NotificationTexts.list("⏰ Heute fällig:", titles));
            }
        }));
    }

    static boolean isUrgent(Task task) {
        return task.assigneeId() != null && task.priority() == TaskPriority.HIGH
                && (task.status() == TaskStatus.TODO || task.status() == TaskStatus.IN_PROGRESS);
    }
}
