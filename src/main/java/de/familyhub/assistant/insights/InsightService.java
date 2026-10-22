package de.familyhub.assistant.insights;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import de.familyhub.assistant.AssistantAccess;
import de.familyhub.assistant.insights.EventConflict.EventRef;
import de.familyhub.calendar.CalendarAccess;
import de.familyhub.calendar.CalendarEvent;
import de.familyhub.calendar.CalendarEventRepository;
import de.familyhub.family.FamilyMember;
import de.familyhub.family.FamilyMemberRepository;
import de.familyhub.task.Task;
import de.familyhub.task.TaskAccess;
import de.familyhub.task.TaskPriority;
import de.familyhub.task.TaskRepository;
import de.familyhub.task.TaskStatus;

// Hinweise ohne KI (Phase 2, Entscheidungen vom 22.10.2026): Terminkonflikte, überfällige Aufgaben und dringende
// Aufgaben, die heute fällig sind. Wer was sieht: jede Person, was sie selbst betrifft (eigene Termine und Aufgaben,
// eigene Vorschläge); wer den Assistenten verwaltet (Administratoren), alles. Zusätzlich muss die Person jeden
// beteiligten Termin und jede Aufgabe ohnehin sehen dürfen, damit kein fremder privater Termin durchsickert.
// Der KI-Assistent nutzt dieselben Hinweise später als Werkzeug.
@Service
public class InsightService {

    // So weit voraus zeigt die Übersicht Terminkonflikte
    static final int CONFLICT_DAYS = 14;

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private final CalendarEventRepository events;
    private final TaskRepository tasks;
    private final FamilyMemberRepository members;
    private final ConflictFinder finder;
    private final CalendarAccess calendarAccess;
    private final TaskAccess taskAccess;
    private final AssistantAccess access;
    private final Clock clock;

    public InsightService(CalendarEventRepository events, TaskRepository tasks, FamilyMemberRepository members,
            ConflictFinder finder, CalendarAccess calendarAccess, TaskAccess taskAccess, AssistantAccess access,
            Clock clock) {
        this.events = events;
        this.tasks = tasks;
        this.members = members;
        this.finder = finder;
        this.calendarAccess = calendarAccess;
        this.taskAccess = taskAccess;
        this.access = access;
        this.clock = clock;
    }

    // Konflikte im Zeitraum [from, to), die die Person sehen darf
    public List<EventConflict> conflictsFor(FamilyMember viewer, LocalDateTime from, LocalDateTime to) {
        List<CalendarEvent> found = events.findOverlapping(from, to);
        Map<String, CalendarEvent> byId = found.stream()
                .collect(Collectors.toMap(CalendarEvent::id, e -> e, (a, b) -> a));
        boolean family = access.seesFamilyInsights(viewer);
        return finder.find(found).stream()
                .filter(c -> family || c.memberIds().contains(viewer.id()) || createdProposal(viewer, c))
                .filter(c -> calendarAccess.canSee(viewer, byId.get(c.first().id()))
                        && calendarAccess.canSee(viewer, byId.get(c.second().id())))
                .toList();
    }

    public List<Insight> insightsFor(FamilyMember viewer) {
        LocalDate today = LocalDate.now(clock);
        Map<String, String> names = new HashMap<>();
        members.findAll().forEach(m -> names.put(m.id(), m.name()));
        Texts texts = new Texts(viewer, names, today);

        List<Insight> result = new ArrayList<>();
        for (EventConflict conflict : conflictsFor(viewer, today.atStartOfDay(),
                today.plusDays(CONFLICT_DAYS).atStartOfDay())) {
            result.add(conflict.involvesProposal()
                    ? new Insight(Insight.Type.PROPOSAL_CONFLICT, conflict.second().start().toLocalDate(),
                            texts.proposalConflict(conflict), conflict.memberIds(), eventIds(conflict), null)
                    : new Insight(Insight.Type.CONFLICT, conflict.second().start().toLocalDate(),
                            texts.conflict(conflict), conflict.memberIds(), eventIds(conflict), null));
        }

        boolean family = access.seesFamilyInsights(viewer);
        List<Task> open = tasks.findAll().stream()
                .filter(t -> t.assigneeId() != null && t.dueDate() != null)
                .filter(t -> t.status() == TaskStatus.TODO || t.status() == TaskStatus.IN_PROGRESS)
                .filter(t -> family || viewer.id().equals(t.assigneeId()))
                .filter(t -> taskAccess.canSee(viewer, t))
                .sorted(Comparator.comparing(Task::dueDate).thenComparing(Task::title))
                .toList();
        open.stream().filter(t -> t.dueDate().isBefore(today))
                .forEach(t -> result.add(taskInsight(Insight.Type.TASK_OVERDUE, t, texts.overdue(t))));
        open.stream().filter(t -> t.dueDate().equals(today) && t.priority() == TaskPriority.HIGH)
                .forEach(t -> result.add(taskInsight(Insight.Type.TASK_DUE_TODAY, t, texts.dueToday(t))));
        return result;
    }

    private static boolean createdProposal(FamilyMember viewer, EventConflict conflict) {
        return Optional.ofNullable(conflict.proposal()).map(p -> viewer.id().equals(p.createdBy())).orElse(false);
    }

    private static List<String> eventIds(EventConflict conflict) {
        return List.of(conflict.first().id(), conflict.second().id());
    }

    private static Insight taskInsight(Insight.Type type, Task task, String text) {
        return new Insight(type, task.dueDate(), text, List.of(task.assigneeId()), List.of(), task.id());
    }

    // Formulierungen; "du", wenn es nur um die angemeldete Person geht
    private record Texts(FamilyMember viewer, Map<String, String> names, LocalDate today) {

        String conflict(EventConflict c) {
            String who = isOnlyViewer(c.memberIds()) ? "Du hast"
                    : join(c.memberIds()) + (c.memberIds().size() == 1 ? " hat" : " haben");
            return who + " " + day(c.second().start().toLocalDate()) + " zwei Termine gleichzeitig: "
                    + event(c.first()) + " und " + event(c.second()) + ".";
        }

        String proposalConflict(EventConflict c) {
            EventRef proposal = c.proposal();
            EventRef other = proposal == c.first() ? c.second() : c.first();
            String owner = viewer.id().equals(proposal.createdBy()) ? "Dein Vorschlag" : "Der Vorschlag";
            String from = viewer.id().equals(proposal.createdBy()) || proposal.createdBy() == null ? ""
                    : " von " + names.getOrDefault(proposal.createdBy(), "einem ehemaligen Mitglied");
            return owner + " " + quoted(proposal.title()) + from + " überschneidet sich "
                    + day(proposal.start().toLocalDate()) + " bei " + atWhom(c.memberIds()) + " mit " + event(other)
                    + ".";
        }

        String overdue(Task t) {
            String since = t.dueDate().equals(today.minusDays(1)) ? "gestern" : "dem " + t.dueDate().format(DATE);
            return (isViewer(t.assigneeId()) ? "Deine Aufgabe " + quoted(t.title())
                    : quoted(t.title()) + " von " + name(t.assigneeId())) + " ist seit " + since + " überfällig.";
        }

        String dueToday(Task t) {
            return isViewer(t.assigneeId()) ? "Deine dringende Aufgabe " + quoted(t.title()) + " ist heute fällig."
                    : quoted(t.title()) + " von " + name(t.assigneeId()) + " ist heute fällig und dringend.";
        }

        // "bei dir", "bei Lucas", "bei dir und Lucas"
        private String atWhom(List<String> memberIds) {
            List<String> list = memberIds.stream().map(this::name).toList();
            return list.size() == 1 ? list.getFirst()
                    : String.join(", ", list.subList(0, list.size() - 1)) + " und " + list.getLast();
        }

        private boolean isOnlyViewer(List<String> memberIds) {
            return memberIds.size() == 1 && isViewer(memberIds.getFirst());
        }

        private boolean isViewer(String memberId) {
            return viewer.id().equals(memberId);
        }

        private String name(String memberId) {
            return isViewer(memberId) ? "dir" : names.getOrDefault(memberId, "einem ehemaligen Mitglied");
        }

        private String join(List<String> memberIds) {
            List<String> list = memberIds.stream()
                    .map(id -> isViewer(id) ? "du" : names.getOrDefault(id, "ein ehemaliges Mitglied"))
                    .toList();
            if (list.size() == 1) {
                return capitalize(list.getFirst());
            }
            return capitalize(String.join(", ", list.subList(0, list.size() - 1)) + " und " + list.getLast());
        }

        private String day(LocalDate date) {
            if (date.equals(today)) {
                return "heute";
            }
            return date.equals(today.plusDays(1)) ? "morgen" : "am " + date.format(DATE);
        }

        private static String event(EventRef e) {
            String time = e.start().toLocalDate().equals(e.end().toLocalDate())
                    ? e.start().format(TIME) + "–" + e.end().format(TIME)
                    : e.start().format(DATE) + " " + e.start().format(TIME) + " – " + e.end().format(DATE) + " "
                            + e.end().format(TIME);
            return quoted(e.title()) + " (" + time + ")";
        }

        private static String quoted(String title) {
            return "„" + title + "“";
        }

        private static String capitalize(String text) {
            return text.isEmpty() ? text : Character.toUpperCase(text.charAt(0)) + text.substring(1);
        }
    }
}
