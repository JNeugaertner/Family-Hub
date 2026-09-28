package de.familyhub.achievements;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.Test;

import de.familyhub.task.Task;
import de.familyhub.task.TaskCategory;
import de.familyhub.task.TaskPriority;

class AchievementProgressTest {

    private static final LocalDate MONDAY = LocalDate.of(2026, 9, 28);

    private static Task task(TaskCategory category, int points) {
        return new Task(null, "Aufgabe", null, "m", MONDAY, TaskPriority.LOW, category, points);
    }

    private static Achievement rule(AchievementRule rule, TaskCategory category) {
        return new Achievement(null, "k", "🏆", "Test", null, rule, category, 1, 0, true);
    }

    @Test
    void countsTasksCategoriesAndPoints() {
        AchievementProgress p = AchievementProgress.empty("m")
                .withConfirmedTask(task(TaskCategory.SCHOOL, 20), MONDAY)
                .withConfirmedTask(task(TaskCategory.SCHOOL, 10), MONDAY)
                .withConfirmedTask(task(TaskCategory.CHORES, 5), MONDAY);

        assertThat(p.valueFor(rule(AchievementRule.TASKS_TOTAL, null))).isEqualTo(3);
        assertThat(p.valueFor(rule(AchievementRule.TASKS_IN_CATEGORY, TaskCategory.SCHOOL))).isEqualTo(2);
        assertThat(p.valueFor(rule(AchievementRule.TASKS_IN_CATEGORY, TaskCategory.HOME))).isZero();
        assertThat(p.valueFor(rule(AchievementRule.POINTS_EARNED, null))).isEqualTo(35);
    }

    @Test
    void streakGrowsOnFollowingDaysRestartsAfterGapAndKeepsTheBest() {
        Achievement streak = rule(AchievementRule.STREAK_DAYS, null);
        AchievementProgress p = AchievementProgress.empty("m")
                .withConfirmedTask(task(TaskCategory.HOME, 5), MONDAY)
                .withConfirmedTask(task(TaskCategory.HOME, 5), MONDAY)
                .withConfirmedTask(task(TaskCategory.HOME, 5), MONDAY.plusDays(1))
                .withConfirmedTask(task(TaskCategory.HOME, 5), MONDAY.plusDays(2));
        assertThat(p.currentStreak()).isEqualTo(3);
        assertThat(p.valueFor(streak)).isEqualTo(3);

        p = p.withConfirmedTask(task(TaskCategory.HOME, 5), MONDAY.plusDays(5));
        assertThat(p.currentStreak()).isEqualTo(1);
        assertThat(p.valueFor(streak)).isEqualTo(3);
    }
}
