package de.familyhub.achievements;

import static de.familyhub.testsupport.TestUsers.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import de.familyhub.family.FamilyMember;
import de.familyhub.family.FamilyMemberRepository;
import de.familyhub.permission.Role;
import de.familyhub.points.PointEntryRepository;
import de.familyhub.points.PointsService;
import de.familyhub.task.Task;
import de.familyhub.task.TaskCategory;
import de.familyhub.task.TaskPriority;
import de.familyhub.task.TaskRepository;
import de.familyhub.task.TaskStatus;
import de.familyhub.testsupport.TestUsers;

// Erfolge über den echten Ablauf: Aufgabe bestätigen -> Zähler -> Erfolg mit Bonuspunkten.
@SpringBootTest
@AutoConfigureMockMvc
class AchievementTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private AchievementRepository achievements;

    @Autowired
    private AchievementProgressRepository progress;

    @Autowired
    private EarnedAchievementRepository earned;

    @Autowired
    private FamilyMemberRepository members;

    @Autowired
    private TaskRepository tasks;

    @Autowired
    private PointEntryRepository pointEntries;

    @Autowired
    private PointsService points;

    private FamilyMember sarah;
    private FamilyMember lucas;
    private FamilyMember oma;

    @BeforeEach
    void setUp() {
        achievements.deleteAll();
        progress.deleteAll();
        earned.deleteAll();
        tasks.deleteAll();
        pointEntries.deleteAll();
        members.deleteAll();
        new AchievementCatalog(achievements).run(null);
        sarah = members.save(TestUsers.member("Sarah", Role.ADMINISTRATOR));
        lucas = members.save(TestUsers.member("Lucas", Role.KIND));
        oma = members.save(TestUsers.member("Oma", Role.GAST));
    }

    private void confirmTask(FamilyMember assignee, TaskCategory category, int taskPoints) throws Exception {
        Task task = tasks.save(new Task(null, "Aufgabe", null, assignee.id(), LocalDate.of(2026, 9, 28),
                TaskPriority.LOW, category, taskPoints).withStatus(TaskStatus.DONE));
        mvc.perform(post("/api/tasks/" + task.id() + "/confirm").with(as(sarah))).andExpect(status().isOk());
    }

    private Achievement byKey(String key) {
        return achievements.findByKey(key).orElseThrow();
    }

    @Test
    void firstConfirmedTaskEarnsFirstAchievementWithBonus() throws Exception {
        confirmTask(lucas, TaskCategory.SCHOOL, 20);

        assertThat(points.balance(lucas.id())).isEqualTo(20 + 10);
        mvc.perform(get("/api/points/history?memberId=" + lucas.id()).with(as(lucas)))
                .andExpect(jsonPath("$[0].reason").value("Erfolg: Erste Aufgabe"))
                .andExpect(jsonPath("$[0].amount").value(10));

        mvc.perform(get("/api/achievements/progress").with(as(lucas)))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].memberId").value(lucas.id()))
                .andExpect(jsonPath("$[0].items[?(@.name == 'Erste Aufgabe')].earnedAt").isNotEmpty())
                .andExpect(jsonPath("$[0].items[?(@.name == 'Fleißig')].current", contains(1)))
                .andExpect(jsonPath("$[0].items[?(@.name == 'Schlaukopf')].current", contains(1)))
                .andExpect(jsonPath("$[0].items[?(@.name == 'Sammler')].current", contains(20)));
    }

    @Test
    void eachAchievementIsEarnedOnlyOnce() throws Exception {
        confirmTask(lucas, TaskCategory.CHORES, 5);
        confirmTask(lucas, TaskCategory.CHORES, 5);

        assertThat(earned.findByMemberIdOrderByEarnedAtDesc(lucas.id())).extracting(EarnedAchievement::name)
                .containsExactly("Erste Aufgabe");
        assertThat(points.balance(lucas.id())).isEqualTo(5 + 5 + 10);
    }

    @Test
    void parentsDoNotCollectAchievements() throws Exception {
        confirmTask(sarah, TaskCategory.HOME, 10);

        assertThat(progress.count()).isZero();
        assertThat(earned.count()).isZero();
    }

    @Test
    void lowerTargetAwardsImmediatelyAndOnlyAdministratorsMayChangeIt() throws Exception {
        confirmTask(lucas, TaskCategory.SCHOOL, 20);
        Achievement schlaukopf = byKey("school-5");

        mvc.perform(put("/api/achievements/" + schlaukopf.id()).with(as(lucas)).contentType(APPLICATION_JSON)
                        .content("{\"active\": true, \"target\": 1, \"bonus\": 70}"))
                .andExpect(status().isForbidden());

        mvc.perform(put("/api/achievements/" + schlaukopf.id()).with(as(sarah)).contentType(APPLICATION_JSON)
                        .content("{\"active\": true, \"target\": 1, \"bonus\": 70}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.target").value(1));

        assertThat(earned.existsByMemberIdAndAchievementId(lucas.id(), schlaukopf.id())).isTrue();
        assertThat(points.balance(lucas.id())).isEqualTo(20 + 10 + 70);
    }

    @Test
    void deactivatedAchievementsAreHiddenFromChildrenButNotFromAdministrators() throws Exception {
        Achievement streak = byKey("streak-7");
        mvc.perform(put("/api/achievements/" + streak.id()).with(as(sarah)).contentType(APPLICATION_JSON)
                        .content("{\"active\": false, \"target\": 7, \"bonus\": 100}"))
                .andExpect(status().isOk());

        mvc.perform(get("/api/achievements").with(as(lucas))).andExpect(jsonPath("$", hasSize(9)));
        mvc.perform(get("/api/achievements").with(as(sarah))).andExpect(jsonPath("$", hasSize(10)));
        mvc.perform(get("/api/achievements/progress").with(as(lucas)))
                .andExpect(jsonPath("$[0].items", hasSize(9)));
    }

    @Test
    void invalidSettingsAreRejectedAndGuestsSeeNothing() throws Exception {
        mvc.perform(put("/api/achievements/" + byKey("tasks-1").id()).with(as(sarah)).contentType(APPLICATION_JSON)
                        .content("{\"active\": true, \"target\": 0, \"bonus\": -5}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.target").value("Das Ziel muss mindestens 1 sein"))
                .andExpect(jsonPath("$.errors.bonus").value("Der Bonus darf nicht negativ sein"));
        mvc.perform(get("/api/achievements/progress").with(as(oma)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Keine Berechtigung, Erfolge anzusehen."));
    }

    @Test
    void catalogIsCompletedButKeepsParentsChanges() {
        Achievement first = byKey("tasks-1");
        achievements.save(first.withSettings(false, 2, 99));
        achievements.delete(byKey("tasks-50"));

        new AchievementCatalog(achievements).run(null);

        assertThat(achievements.count()).isEqualTo(10);
        assertThat(byKey("tasks-1").bonus()).isEqualTo(99);
        assertThat(byKey("tasks-1").active()).isFalse();
    }
}
