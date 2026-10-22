package de.familyhub.assistant.insights;

import static de.familyhub.testsupport.TestUsers.as;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import de.familyhub.calendar.CalendarEvent;
import de.familyhub.calendar.CalendarEventRepository;
import de.familyhub.calendar.EventCategory;
import de.familyhub.calendar.EventStatus;
import de.familyhub.family.FamilyMember;
import de.familyhub.family.FamilyMemberRepository;
import de.familyhub.permission.Role;
import de.familyhub.task.Task;
import de.familyhub.task.TaskCategory;
import de.familyhub.task.TaskPriority;
import de.familyhub.task.TaskRepository;
import de.familyhub.task.TaskStatus;
import de.familyhub.testsupport.TestUsers;

// Hinweise ohne KI (Entscheidungen vom 22.10.2026): jede Person ihre eigenen, Administratoren alle; nie Termine,
// die jemand gar nicht sehen darf
@SpringBootTest
@AutoConfigureMockMvc
class InsightTest {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.");

    @Autowired
    private MockMvc mvc;

    @Autowired
    private FamilyMemberRepository members;

    @Autowired
    private CalendarEventRepository events;

    @Autowired
    private TaskRepository tasks;

    private FamilyMember sarah;
    private FamilyMember emma;
    private FamilyMember lucas;
    private FamilyMember oma;
    private final LocalDate today = LocalDate.now();
    // Übermorgen: weder "heute" noch "morgen", sondern ein Datum im Text
    private final LocalDate day = today.plusDays(2);

    @BeforeEach
    void setUp() {
        events.deleteAll();
        tasks.deleteAll();
        members.deleteAll();
        sarah = members.save(TestUsers.member("Sarah", Role.ADMINISTRATOR));
        emma = members.save(TestUsers.member("Emma", Role.JUGENDLICHER));
        lucas = members.save(TestUsers.member("Lucas", Role.KIND));
        oma = members.save(TestUsers.member("Oma", Role.GAST));
    }

    private CalendarEvent event(String title, LocalDate date, int from, int to, EventStatus status,
            FamilyMember createdBy, FamilyMember... who) {
        return events.save(new CalendarEvent(null, title, date.atTime(from, 0), date.atTime(to, 0),
                List.of(who).stream().map(FamilyMember::id).toList(), EventCategory.FAMILY, null, null, false,
                status, createdBy.id(), null));
    }

    private CalendarEvent event(String title, int from, int to, FamilyMember... who) {
        return event(title, day, from, to, EventStatus.APPROVED, sarah, who);
    }

    private Task task(String title, FamilyMember assignee, LocalDate due, TaskPriority priority, TaskStatus status) {
        return tasks.save(new Task(null, title, null, assignee.id(), due, priority, TaskCategory.CHORES, 0, status,
                sarah.id(), null, null));
    }

    private void familyWithConflictsAndTasks() {
        event("Training", 16, 18, lucas);
        event("Klavier", 17, 18, lucas);
        event("Fußball", 15, 17, emma);
        event("Zahnarzt", 16, 17, emma);
        task("Zimmer aufräumen", lucas, today.minusDays(3), TaskPriority.LOW, TaskStatus.TODO);
        task("Müll rausbringen", lucas, today, TaskPriority.HIGH, TaskStatus.IN_PROGRESS);
        task("Geige üben", emma, today.minusDays(1), TaskPriority.MEDIUM, TaskStatus.TODO);
        // Erledigt, nicht dringend genug oder ohne Frist: kein Hinweis
        task("Schon erledigt", lucas, today.minusDays(2), TaskPriority.HIGH, TaskStatus.DONE);
        task("Heute, aber nicht dringend", lucas, today, TaskPriority.MEDIUM, TaskStatus.TODO);
    }

    @Test
    void everyoneSeesWhatConcernsThem() throws Exception {
        familyWithConflictsAndTasks();

        mvc.perform(get("/api/assistant/insights").with(as(lucas)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].type", containsInAnyOrder("conflict", "task_overdue", "task_due_today")))
                .andExpect(jsonPath("$[0].text").value("Du hast am " + day.format(DATE)
                        + " zwei Termine gleichzeitig: „Training“ (16:00–18:00) und „Klavier“ (17:00–18:00)."))
                .andExpect(jsonPath("$[1].text").value("Deine Aufgabe „Zimmer aufräumen“ ist seit dem "
                        + today.minusDays(3).format(DATE) + " überfällig."))
                .andExpect(jsonPath("$[2].text").value("Deine dringende Aufgabe „Müll rausbringen“ ist heute fällig."));
    }

    @Test
    void administratorsSeeTheWholeFamily() throws Exception {
        familyWithConflictsAndTasks();

        mvc.perform(get("/api/assistant/insights").with(as(sarah)))
                .andExpect(jsonPath("$.length()").value(5))
                .andExpect(jsonPath("$[0].text").value("Emma hat am " + day.format(DATE)
                        + " zwei Termine gleichzeitig: „Fußball“ (15:00–17:00) und „Zahnarzt“ (16:00–17:00)."))
                .andExpect(jsonPath("$[0].memberIds[0]").value(emma.id()))
                .andExpect(jsonPath("$[0].eventIds.length()").value(2))
                .andExpect(jsonPath("$[2].text").value("„Zimmer aufräumen“ von Lucas ist seit dem "
                        + today.minusDays(3).format(DATE) + " überfällig."))
                .andExpect(jsonPath("$[3].text").value("„Geige üben“ von Emma ist seit gestern überfällig."))
                .andExpect(jsonPath("$[4].text").value("„Müll rausbringen“ von Lucas ist heute fällig und dringend."));
    }

    @Test
    void proposalsShowTheirConflictToTheProposerAndTheParentsOnly() throws Exception {
        event("Training", day, 17, 19, EventStatus.APPROVED, sarah, lucas);
        event("Kino", day, 18, 20, EventStatus.PROPOSED, emma, emma, lucas);

        mvc.perform(get("/api/assistant/insights").with(as(emma)))
                .andExpect(jsonPath("$[0].type").value("proposal_conflict"))
                .andExpect(jsonPath("$[0].text").value("Dein Vorschlag „Kino“ überschneidet sich am "
                        + day.format(DATE) + " bei Lucas mit „Training“ (17:00–19:00)."));
        mvc.perform(get("/api/assistant/insights").with(as(sarah)))
                .andExpect(jsonPath("$[0].text").value("Der Vorschlag „Kino“ von Emma überschneidet sich am "
                        + day.format(DATE) + " bei Lucas mit „Training“ (17:00–19:00)."));
        // Lucas ist beteiligt, sieht den Vorschlag seiner Schwester aber noch nicht
        mvc.perform(get("/api/assistant/insights").with(as(lucas)))
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void conflictsForTheCalendarStartTodayUnlessARangeIsGiven() throws Exception {
        event("Gestern A", today.minusDays(1), 10, 12, EventStatus.APPROVED, sarah, sarah);
        event("Gestern B", today.minusDays(1), 11, 13, EventStatus.APPROVED, sarah, sarah);
        event("Bald A", 10, 12, sarah);
        event("Bald B", 11, 13, sarah);

        mvc.perform(get("/api/assistant/conflicts").with(as(sarah)))
                .andExpect(jsonPath("$[*].first.title", containsInAnyOrder("Bald A")));
        LocalDateTime from = today.minusDays(7).atStartOfDay();
        mvc.perform(get("/api/assistant/conflicts").with(as(sarah))
                        .param("from", from.toString()).param("to", from.plusDays(30).toString()))
                .andExpect(jsonPath("$[*].first.title", containsInAnyOrder("Gestern A", "Bald A")));
        mvc.perform(get("/api/assistant/conflicts").with(as(sarah)).param("from", from.toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void guestsGetNoInsights() throws Exception {
        mvc.perform(get("/api/assistant/insights").with(as(oma))).andExpect(status().isForbidden());
        mvc.perform(get("/api/assistant/conflicts").with(as(oma))).andExpect(status().isForbidden());
    }
}
