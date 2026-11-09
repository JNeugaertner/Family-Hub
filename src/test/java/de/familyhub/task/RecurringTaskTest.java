package de.familyhub.task;

import static de.familyhub.testsupport.TestUsers.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.not;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;

import de.familyhub.family.FamilyMember;
import de.familyhub.family.FamilyMemberRepository;
import de.familyhub.permission.Role;
import de.familyhub.points.PointEntryRepository;
import de.familyhub.testsupport.TestUsers;

// Wiederkehrende Aufgaben (Entscheidungen vom 04.11.2026): Wer eine Runde abhakt, erzeugt sofort die nächste, fällig
// am Tag des Abhakens plus Abstand und offen für alle (wer zuerst kommt). Wird der Haken zurückgenommen, verschwindet
// die nächste Runde wieder, solange sie noch niemand übernommen hat.
@SpringBootTest
@AutoConfigureMockMvc
class RecurringTaskTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private TaskRepository tasks;

    @Autowired
    private FamilyMemberRepository members;

    @Autowired
    private PointEntryRepository points;

    private final LocalDate today = LocalDate.now();

    private FamilyMember sarah;
    private FamilyMember emma;
    private FamilyMember lucas;
    private FamilyMember lily;
    private FamilyMember oma;

    @BeforeEach
    void setUp() {
        tasks.deleteAll();
        points.deleteAll();
        members.deleteAll();
        sarah = members.save(TestUsers.member("Sarah", Role.ADMINISTRATOR));
        emma = members.save(TestUsers.member("Emma", Role.JUGENDLICHER));
        lucas = members.save(TestUsers.member("Lucas", Role.KIND));
        lily = members.save(TestUsers.member("Lily", Role.KIND));
        oma = members.save(TestUsers.member("Oma", Role.GAST));
    }

    private static String recurring(String title, LocalDate due, int interval, String unit, int pointValue) {
        return """
                {"title": "%s", "dueDate": "%s", "priority": "low", "category": "home", "points": %d,
                 "repeat": {"interval": %d, "unit": "%s"}}
                """.formatted(title, due, pointValue, interval, unit);
    }

    // Feste Person: jede neue Runde geht wieder an sie
    private static String recurringFor(FamilyMember person, String title, int interval, int pointValue) {
        return """
                {"title": "%s", "dueDate": "%s", "priority": "low", "category": "home", "points": %d,
                 "assigneeId": "%s", "repeat": {"interval": %d, "unit": "days", "openToAll": false}}
                """.formatted(title, LocalDate.now(), pointValue, person.id(), interval);
    }

    private String create(String body) throws Exception {
        return create(sarah, body);
    }

    private String create(FamilyMember who, String body) throws Exception {
        String response = mvc.perform(post("/api/tasks").with(as(who)).contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    private ResultActions changeStatus(FamilyMember who, String id, String newStatus) throws Exception {
        return mvc.perform(patch("/api/tasks/" + id + "/status").with(as(who)).contentType(APPLICATION_JSON)
                .content("{\"status\": \"" + newStatus + "\"}"));
    }

    private List<Task> roundsAfter(String id) {
        return tasks.findByPreviousRoundTaskId(id);
    }

    @Test
    void parentsCreateOpenRecurringTasksWithoutPerson() throws Exception {
        mvc.perform(post("/api/tasks").with(as(sarah)).contentType(APPLICATION_JSON)
                        .content(recurring("Blumen gießen", today, 3, "days", 10)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.assigneeId").doesNotExist())
                .andExpect(jsonPath("$.repeat.interval").value(3))
                .andExpect(jsonPath("$.repeat.unit").value("days"))
                .andExpect(jsonPath("$.repeat.openToAll").value(true))
                .andExpect(jsonPath("$.points").value(10));

        // Jugendliche legen nur eigene Aufgaben an, jede neue Runde wäre aber für alle offen
        mvc.perform(post("/api/tasks").with(as(emma)).contentType(APPLICATION_JSON)
                        .content(recurring("Blumen gießen", today, 3, "days", 0).replace("\"points\": 0,",
                                "\"points\": 0, \"assigneeId\": \"" + emma.id() + "\",")
                                .replace("\"unit\": \"days\"", "\"unit\": \"days\", \"openToAll\": true")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Wiederkehrende Aufgaben für alle (wer zuerst kommt) darf nur "
                        + "anlegen und ändern, wer Aufgaben für die ganze Familie anlegt."));

        mvc.perform(post("/api/tasks").with(as(sarah)).contentType(APPLICATION_JSON)
                        .content(recurring("Zu oft", today, 0, "days", 0)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors['repeat.interval']").value("Der Abstand muss mindestens 1 sein"));
        mvc.perform(post("/api/tasks").with(as(sarah)).contentType(APPLICATION_JSON)
                        .content(recurring("Ohne Einheit", today, 2, "days", 0).replace(", \"unit\": \"days\"", "")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors['repeat.unit']").value("Einheit ist Pflicht (days, weeks oder months)"));
        // Ohne Wiederholung braucht eine Aufgabe weiterhin eine Person
        mvc.perform(post("/api/tasks").with(as(sarah)).contentType(APPLICATION_JSON).content("""
                        {"title": "Einmalig", "dueDate": "%s", "priority": "low", "category": "home"}
                        """.formatted(today)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.assigneeId").value("Aufgabe muss einem Familienmitglied zugewiesen sein"));
    }

    @Test
    void openRoundsAreVisibleToEveryoneWhoTicksTasksButNotToGuests() throws Exception {
        create(recurring("Blumen gießen", today, 3, "days", 10));

        mvc.perform(get("/api/tasks").with(as(lucas))).andExpect(jsonPath("$[*].title", hasItem("Blumen gießen")));
        mvc.perform(get("/api/tasks").with(as(emma))).andExpect(jsonPath("$[*].title", hasItem("Blumen gießen")));
        mvc.perform(get("/api/tasks").with(as(oma))).andExpect(jsonPath("$[*].title", not(hasItem("Blumen gießen"))));
    }

    @Test
    void tickingAnOpenRoundTakesItAndCreatesTheNextOpenRound() throws Exception {
        String id = create(recurring("Blumen gießen", today, 3, "days", 10));

        changeStatus(lucas, id, "done")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assigneeId").value(lucas.id()))
                .andExpect(jsonPath("$.status").value("done"))
                .andExpect(jsonPath("$.points").value(10));

        assertThat(roundsAfter(id)).singleElement().satisfies(next -> {
            assertThat(next.title()).isEqualTo("Blumen gießen");
            assertThat(next.assigneeId()).isNull();
            assertThat(next.status()).isEqualTo(TaskStatus.TODO);
            assertThat(next.dueDate()).isEqualTo(today.plusDays(3));
            assertThat(next.points()).isEqualTo(10);
            assertThat(next.repeat()).isEqualTo(new TaskRepeat(3, RepeatUnit.DAYS, true));
            assertThat(next.previousRound()).isEqualTo(new PreviousRound(id, today, lucas.id()));
        });
        // Die erledigte Runde wartet wie jede Aufgabe mit Punkten auf die Bestätigung
        mvc.perform(post("/api/tasks/" + id + "/confirm").with(as(sarah))).andExpect(status().isOk());
        assertThat(points.findAll()).singleElement().satisfies(p -> {
            assertThat(p.memberId()).isEqualTo(lucas.id());
            assertThat(p.amount()).isEqualTo(10);
        });
        assertThat(tasks.findAll()).filteredOn(Task::isOpenRound).hasSize(1);
    }

    @Test
    void nextRoundCountsFromTheDayItWasDoneNotFromTheDueDate() throws Exception {
        String late = create(recurring("Filter wechseln", today.minusDays(5), 2, "weeks", 0));
        String early = create(recurring("Bettwäsche", today.plusDays(10), 1, "months", 0));

        changeStatus(emma, late, "done").andExpect(status().isOk());
        changeStatus(emma, early, "done").andExpect(status().isOk());

        assertThat(roundsAfter(late)).singleElement()
                .satisfies(next -> assertThat(next.dueDate()).isEqualTo(today.plusWeeks(2)));
        assertThat(roundsAfter(early)).singleElement()
                .satisfies(next -> assertThat(next.dueDate()).isEqualTo(today.plusMonths(1)));
    }

    // Anforderung 8: Änderungen am Wiederholungsintervall werden für zukünftige Wiederholungen übernommen
    @Test
    void changedIntervalAppliesToTheFollowingRounds() throws Exception {
        String id = create(recurring("Blumen gießen", today, 3, "days", 10));

        mvc.perform(put("/api/tasks/" + id).with(as(sarah)).contentType(APPLICATION_JSON)
                        .content(recurring("Blumen gießen", today, 1, "weeks", 10)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.repeat.interval").value(1))
                .andExpect(jsonPath("$.repeat.unit").value("weeks"))
                .andExpect(jsonPath("$.dueDate").value(today.toString()));
        changeStatus(lucas, id, "done").andExpect(status().isOk());

        assertThat(roundsAfter(id)).singleElement().satisfies(next -> {
            assertThat(next.dueDate()).isEqualTo(today.plusWeeks(1));
            assertThat(next.repeat().interval()).isEqualTo(1);
            assertThat(next.repeat().unit()).isEqualTo(RepeatUnit.WEEKS);
        });
        // und auch die Runde danach
        String nextId = roundsAfter(id).getFirst().id();
        changeStatus(emma, nextId, "done").andExpect(status().isOk());
        assertThat(roundsAfter(nextId)).singleElement()
                .satisfies(after -> assertThat(after.dueDate()).isEqualTo(today.plusWeeks(1)));
    }

    @Test
    void adultsTakeRoundsWithoutPointsButTheNextRoundKeepsThem() throws Exception {
        String id = create(recurring("Blumen gießen", today, 3, "days", 10));

        changeStatus(sarah, id, "done")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assigneeId").value(sarah.id()))
                .andExpect(jsonPath("$.points").value(0));
        assertThat(tasks.findById(id)).get().satisfies(round -> assertThat(round.isCompleted()).isTrue());
        assertThat(roundsAfter(id)).singleElement().satisfies(next -> assertThat(next.points()).isEqualTo(10));

        // Haken zurück: Die Runde bekommt ihre Punkte wieder, die nächste verschwindet
        changeStatus(sarah, id, "inprogress")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.points").value(10));
        assertThat(roundsAfter(id)).isEmpty();
    }

    @Test
    void untickingRemovesTheNextRoundOnlyWhileNobodyHasTakenIt() throws Exception {
        String id = create(recurring("Blumen gießen", today, 3, "days", 10));
        changeStatus(lucas, id, "done").andExpect(status().isOk());

        changeStatus(lucas, id, "todo")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assigneeId").value(lucas.id()))
                .andExpect(jsonPath("$.status").value("todo"));
        assertThat(roundsAfter(id)).isEmpty();
        assertThat(tasks.count()).isEqualTo(1);

        changeStatus(lucas, id, "done").andExpect(status().isOk());
        String next = roundsAfter(id).getFirst().id();
        mvc.perform(post("/api/tasks/" + next + "/claim").with(as(lily))).andExpect(status().isOk());
        changeStatus(lucas, id, "inprogress").andExpect(status().isOk());

        assertThat(tasks.findById(next)).get().satisfies(round -> assertThat(round.assigneeId()).isEqualTo(lily.id()));
    }

    @Test
    void reopeningByAParentRemovesTheUntouchedNextRound() throws Exception {
        String id = create(recurring("Blumen gießen", today, 3, "days", 10));
        changeStatus(lucas, id, "done").andExpect(status().isOk());

        mvc.perform(post("/api/tasks/" + id + "/reopen").with(as(sarah)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("inprogress"));
        assertThat(roundsAfter(id)).isEmpty();
    }

    @Test
    void firstToTakeAnOpenRoundGetsIt() throws Exception {
        String id = create(recurring("Blumen gießen", today, 3, "days", 10));

        mvc.perform(post("/api/tasks/" + id + "/claim").with(as(emma)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assigneeId").value(emma.id()))
                .andExpect(jsonPath("$.status").value("inprogress"));
        mvc.perform(post("/api/tasks/" + id + "/claim").with(as(sarah)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Diese Aufgabe hat schon jemand übernommen."));
        changeStatus(lily, id, "done").andExpect(status().isNotFound());

        mvc.perform(post("/api/tasks/" + id + "/release").with(as(lucas))).andExpect(status().isNotFound());
        mvc.perform(post("/api/tasks/" + id + "/release").with(as(emma)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assigneeId").doesNotExist())
                .andExpect(jsonPath("$.status").value("todo"));

        changeStatus(lily, id, "done").andExpect(status().isOk());
        mvc.perform(post("/api/tasks/" + id + "/release").with(as(lily)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Erledigte Aufgaben lassen sich nicht mehr zurückgeben."));
    }

    @Test
    void tickingTwiceCreatesOnlyOneNextRound() throws Exception {
        String id = create(recurring("Blumen gießen", today, 3, "days", 10));

        changeStatus(lucas, id, "done").andExpect(status().isOk());
        changeStatus(lucas, id, "done").andExpect(status().isOk());

        assertThat(roundsAfter(id)).hasSize(1);
    }

    @Test
    void parentsEndTheRepetitionChildrenCannot() throws Exception {
        String id = create(recurring("Blumen gießen", today, 3, "days", 10));
        String withoutRepeat = """
                {"title": "Blumen gießen", "dueDate": "%s", "priority": "low", "category": "home", "points": 10,
                 "assigneeId": "%s"}
                """.formatted(today, emma.id());
        mvc.perform(post("/api/tasks/" + id + "/claim").with(as(emma))).andExpect(status().isOk());

        mvc.perform(put("/api/tasks/" + id).with(as(emma)).contentType(APPLICATION_JSON).content(withoutRepeat))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/tasks/" + id).with(as(sarah)).contentType(APPLICATION_JSON).content(withoutRepeat))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.repeat").doesNotExist());

        changeStatus(emma, id, "done").andExpect(status().isOk());
        assertThat(roundsAfter(id)).isEmpty();
        assertThat(tasks.count()).isEqualTo(1);
    }

    @Test
    void bonusTasksKeepTheirOwnRepetition() throws Exception {
        mvc.perform(post("/api/tasks").with(as(sarah)).contentType(APPLICATION_JSON).content("""
                        {"title": "Auto waschen", "bonus": true, "repeatable": true, "points": 30, "priority": "medium",
                         "category": "chores", "repeat": {"interval": 3, "unit": "days"}}
                        """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.repeatable").value(true))
                .andExpect(jsonPath("$.repeat").doesNotExist());
    }

    // Feste Person (Erweiterung vom 04.11.2026 nach dem Abgleich mit Anforderung 8)
    @Test
    void aFixedPersonGetsEveryRound() throws Exception {
        String id = create(recurringFor(lucas, "Hamster füttern", 2, 10));

        // gehört Lucas und ist nicht für alle offen
        mvc.perform(get("/api/tasks").with(as(lily))).andExpect(jsonPath("$[*].title", not(hasItem("Hamster füttern"))));
        mvc.perform(post("/api/tasks/" + id + "/claim").with(as(emma))).andExpect(status().isConflict());
        mvc.perform(post("/api/tasks/" + id + "/release").with(as(lucas)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Diese Aufgabe gehört fest zu einer Person und lässt sich nicht freigeben."));

        changeStatus(lucas, id, "done").andExpect(status().isOk());
        assertThat(roundsAfter(id)).singleElement().satisfies(next -> {
            assertThat(next.assigneeId()).isEqualTo(lucas.id());
            assertThat(next.status()).isEqualTo(TaskStatus.TODO);
            assertThat(next.dueDate()).isEqualTo(today.plusDays(2));
            assertThat(next.points()).isEqualTo(10);
            assertThat(next.repeat().nextRoundOpenToAll()).isFalse();
        });
        mvc.perform(get("/api/tasks").with(as(lily))).andExpect(jsonPath("$[*].title", not(hasItem("Hamster füttern"))));

        // Haken zurück: Die nächste Runde verschwindet, solange Lucas sie nicht angefangen hat
        changeStatus(lucas, id, "todo").andExpect(status().isOk());
        assertThat(roundsAfter(id)).isEmpty();
    }

    @Test
    void teensSetUpTheirOwnRecurringTasks() throws Exception {
        // ohne openToAll: Mit Person geht jede Runde an sie
        String id = create(emma, """
                {"title": "Gitarre üben", "dueDate": "%s", "priority": "medium", "category": "school",
                 "assigneeId": "%s", "repeat": {"interval": 1, "unit": "days"}}
                """.formatted(today, emma.id()));
        assertThat(tasks.findById(id)).get().satisfies(t -> assertThat(t.repeat().nextRoundOpenToAll()).isFalse());
        // für andere weiterhin nicht
        mvc.perform(post("/api/tasks").with(as(emma)).contentType(APPLICATION_JSON)
                        .content(recurringFor(lucas, "Für Lucas", 2, 0)))
                .andExpect(status().isForbidden());

        mvc.perform(put("/api/tasks/" + id).with(as(emma)).contentType(APPLICATION_JSON).content("""
                        {"title": "Gitarre üben", "dueDate": "%s", "priority": "medium", "category": "school",
                         "assigneeId": "%s", "repeat": {"interval": 2, "unit": "days"}}
                        """.formatted(today, emma.id())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.repeat.interval").value(2))
                .andExpect(jsonPath("$.repeat.openToAll").value(false));
        changeStatus(emma, id, "done").andExpect(status().isOk());
        assertThat(roundsAfter(id)).singleElement().satisfies(next -> {
            assertThat(next.assigneeId()).isEqualTo(emma.id());
            assertThat(next.dueDate()).isEqualTo(today.plusDays(2));
        });
    }

    @Test
    void fixedRecurringTasksNeedAPersonAndAdultsGetNoPoints() throws Exception {
        mvc.perform(post("/api/tasks").with(as(sarah)).contentType(APPLICATION_JSON)
                        .content(recurring("Ohne Person", today, 3, "days", 0)
                                .replace("\"unit\": \"days\"", "\"unit\": \"days\", \"openToAll\": false")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.assigneeId").value("Aufgabe muss einem Familienmitglied zugewiesen sein"));

        mvc.perform(post("/api/tasks").with(as(sarah)).contentType(APPLICATION_JSON)
                        .content(recurringFor(sarah, "Filter wechseln", 30, 20)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.points").value(0));
    }

    @Test
    void parentsSwitchBetweenOpenAndFixed() throws Exception {
        String id = create(recurring("Blumen gießen", today, 3, "days", 10));
        mvc.perform(put("/api/tasks/" + id).with(as(sarah)).contentType(APPLICATION_JSON)
                        .content(recurringFor(lucas, "Blumen gießen", 3, 10)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assigneeId").value(lucas.id()))
                .andExpect(jsonPath("$.repeat.openToAll").value(false));
        changeStatus(lucas, id, "done").andExpect(status().isOk());
        assertThat(roundsAfter(id)).singleElement()
                .satisfies(next -> assertThat(next.assigneeId()).isEqualTo(lucas.id()));

        // Eine übernommene Runde bleibt beim Bearbeiten für alle offen, wenn openToAll mitkommt
        String litter = create(recurring("Katzenklo", today, 2, "days", 5));
        mvc.perform(post("/api/tasks/" + litter + "/claim").with(as(lily))).andExpect(status().isOk());
        mvc.perform(put("/api/tasks/" + litter).with(as(sarah)).contentType(APPLICATION_JSON).content("""
                        {"title": "Katzenklo sauber machen", "dueDate": "%s", "priority": "low", "category": "home",
                         "points": 5, "assigneeId": "%s", "repeat": {"interval": 2, "unit": "days", "openToAll": true}}
                        """.formatted(today, lily.id())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assigneeId").value(lily.id()));
        changeStatus(lily, litter, "done").andExpect(status().isOk());
        assertThat(roundsAfter(litter)).singleElement().satisfies(next -> assertThat(next.assigneeId()).isNull());
    }
}
