package de.familyhub.waste;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.web.multipart.MultipartFile;

import de.familyhub.family.FamilyMember;
import de.familyhub.family.FamilyMemberRepository;
import de.familyhub.permission.Role;
import de.familyhub.task.Task;
import de.familyhub.task.TaskAccess;
import de.familyhub.task.TaskCategory;
import de.familyhub.task.TaskPriority;
import de.familyhub.task.TaskRepository;
import de.familyhub.task.TaskStatus;
import de.familyhub.web.ApiException;

@Service
public class WasteCollectionService {

    private static final int MAX_FILE_SIZE = 2 * 1024 * 1024;
    private static final DateTimeFormatter BASIC_DATE = DateTimeFormatter.ofPattern("yyyyMMdd", Locale.ROOT);
    private static final String TASK_DESCRIPTION = "Automatisch aus dem Müllabfuhrkalender erstellt.";

    private final WasteCollectionRepository collections;
    private final TaskRepository tasks;
    private final FamilyMemberRepository members;
    private final TaskAccess taskAccess;
    private final Clock clock;

    public WasteCollectionService(WasteCollectionRepository collections, TaskRepository tasks,
            FamilyMemberRepository members, TaskAccess taskAccess, Clock clock) {
        this.collections = collections;
        this.tasks = tasks;
        this.members = members;
        this.taskAccess = taskAccess;
        this.clock = clock;
    }

    public WasteCollection current() {
        return collections.current();
    }

    public WasteCollection importCalendar(MultipartFile file, int points, String assigneeId, FamilyMember creator) {
        if (file == null || file.isEmpty() || file.getOriginalFilename() == null
                || !file.getOriginalFilename().toLowerCase(Locale.ROOT).endsWith(".ics")) {
            throw ApiException.invalidField("file", "Bitte eine gültige .ics-Datei auswählen.");
        }
        if (file.getSize() > MAX_FILE_SIZE) {
            throw ApiException.invalidField("file", "Die ICS-Datei darf höchstens 2 MB groß sein.");
        }
        if (points < 0 || points > 1000) {
            throw ApiException.invalidField("points", "Punkte müssen zwischen 0 und 1000 liegen.");
        }
        String assignedMember = assigneeId == null || assigneeId.isBlank() ? null : assigneeId;
        if (assignedMember != null && !members.existsById(assignedMember)) {
            throw ApiException.invalidField("assigneeId", "Familienmitglied existiert nicht.");
        }
        if (assignedMember == null && points == 0) {
            throw ApiException.invalidField("points", "Offene Aufgaben benötigen mindestens einen Punkt.");
        }

        List<WasteCollection.Pickup> pickups;
        try {
            pickups = parse(file.getBytes());
        } catch (IOException e) {
            throw ApiException.invalidField("file", "Die ICS-Datei konnte nicht gelesen werden.");
        }
        if (pickups.isEmpty()) {
            throw ApiException.invalidField("file", "Die ICS-Datei enthält keine Müllabfuhrtermine.");
        }

        WasteCollection imported = collections.save(new WasteCollection(WasteCollection.ID, file.getOriginalFilename(),
                LocalDateTime.now(clock), points, assignedMember, creator.id(), pickups));
        createUpcomingTasks(imported, creator);
        return imported;
    }

    @Scheduled(cron = "${familyhub.waste.task-sync-cron:0 10 0 * * *}")
    public void createUpcomingTasks() {
        WasteCollection collection = collections.current();
        if (collection == null) {
            return;
        }
        FamilyMember creator = collection.createdBy() == null ? null
                : members.findById(collection.createdBy()).orElse(null);
        if (creator == null) {
            creator = members.findAll().stream().filter(member -> member.role() == Role.ADMINISTRATOR)
                    .findFirst().orElse(null);
        }
        if (creator != null) {
            createUpcomingTasks(collection, creator);
        }
    }

    private void createUpcomingTasks(WasteCollection collection, FamilyMember creator) {
        LocalDate today = LocalDate.now(clock);
        Set<String> activeTasks = collection.pickups().stream()
                .filter(pickup -> !pickup.date().isBefore(today))
                .map(pickup -> taskKey(title(pickup), pickup.date().minusDays(1)))
                .collect(Collectors.toSet());
        for (WasteCollection.Pickup pickup : collection.pickups()) {
            long daysUntilPickup = ChronoUnit.DAYS.between(today, pickup.date());
            if (daysUntilPickup < 1 || daysUntilPickup > 5) {
                continue;
            }
            LocalDate dueDate = pickup.date().minusDays(1);
            String title = title(pickup);
            List<Task> existing = tasks.findByTitleAndDueDateAndDescription(title, dueDate, TASK_DESCRIPTION);
            if (!existing.isEmpty()) {
                Task task = existing.getFirst();
                if (task.status() == TaskStatus.TODO) {
                    Task updated = new Task(task.id(), title, TASK_DESCRIPTION, collection.assigneeId(), dueDate,
                            TaskPriority.HIGH, TaskCategory.CHORES, collection.points(), TaskStatus.TODO,
                            task.createdBy(), null, null, collection.assigneeId() == null, false);
                    taskAccess.requireCreate(creator, updated);
                    tasks.save(updated);
                }
                continue;
            }
            Task task = new Task(null, title, TASK_DESCRIPTION, collection.assigneeId(), dueDate,
                    TaskPriority.HIGH, TaskCategory.CHORES, collection.points(), TaskStatus.TODO, creator.id(), null,
                    null, collection.assigneeId() == null, false);
            taskAccess.requireCreate(creator, task);
            tasks.save(task);
        }
        for (Task existing : tasks.findByDescription(TASK_DESCRIPTION)) {
            if (existing.dueDate() != null && existing.status() == TaskStatus.TODO
                    && !activeTasks.contains(taskKey(existing.title(), existing.dueDate()))) {
                tasks.delete(existing);
            }
        }
    }

    private static String title(WasteCollection.Pickup pickup) {
        return pickup.type() + " rausbringen";
    }

    private static String taskKey(String title, LocalDate dueDate) {
        return title + "|" + dueDate;
    }

    private static List<WasteCollection.Pickup> parse(byte[] bytes) {
        String source = new String(bytes, StandardCharsets.UTF_8);
        List<String> lines = unfold(source);
        List<WasteCollection.Pickup> pickups = new ArrayList<>();
        boolean inEvent = false;
        String uid = null;
        String summary = null;
        LocalDate date = null;
        for (String line : lines) {
            if (line.equalsIgnoreCase("BEGIN:VEVENT")) {
                inEvent = true;
                uid = null;
                summary = null;
                date = null;
            } else if (inEvent && line.equalsIgnoreCase("END:VEVENT")) {
                if (date != null && summary != null && !summary.isBlank()) {
                    String type = unescape(summary).replaceFirst("(?i)^Entsorgung:\\s*", "").trim();
                    if (!type.isEmpty()) {
                        pickups.add(new WasteCollection.Pickup(uid == null ? type + ":" + date : uid, type, date));
                    }
                }
                inEvent = false;
            } else if (inEvent) {
                int separator = line.indexOf(':');
                if (separator < 0) {
                    continue;
                }
                String name = line.substring(0, separator).split(";", 2)[0].toUpperCase(Locale.ROOT);
                String value = line.substring(separator + 1);
                if (name.equals("UID")) {
                    uid = value;
                } else if (name.equals("SUMMARY")) {
                    summary = value;
                } else if (name.equals("DTSTART")) {
                    date = parseDate(value);
                }
            }
        }
        return pickups.stream().distinct().sorted((a, b) -> a.date().compareTo(b.date())).toList();
    }

    private static LocalDate parseDate(String value) {
        try {
            if (value.length() >= 8) {
                return LocalDate.parse(value.substring(0, 8), BASIC_DATE);
            }
        } catch (DateTimeParseException e) {
            throw ApiException.invalidField("file", "Ein Müllabfuhrtermin enthält ein ungültiges Datum.");
        }
        throw ApiException.invalidField("file", "Ein Müllabfuhrtermin enthält kein Startdatum.");
    }

    private static List<String> unfold(String source) {
        List<String> result = new ArrayList<>();
        for (String line : source.replace("\r\n", "\n").replace('\r', '\n').split("\n")) {
            if (!result.isEmpty() && !line.isEmpty() && (line.charAt(0) == ' ' || line.charAt(0) == '\t')) {
                int last = result.size() - 1;
                result.set(last, result.get(last) + line.substring(1));
            } else {
                result.add(line);
            }
        }
        return result;
    }

    private static String unescape(String value) {
        return value.replace("\\n", " ").replace("\\N", " ").replace("\\,", ",")
                .replace("\\;", ";").replace("\\\\", "\\");
    }
}