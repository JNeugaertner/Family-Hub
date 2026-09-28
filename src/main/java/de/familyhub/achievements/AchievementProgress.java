package de.familyhub.achievements;

import java.time.LocalDate;
import java.util.EnumMap;
import java.util.Map;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import de.familyhub.task.Task;
import de.familyhub.task.TaskCategory;

// Zähler je Kind, fortgeschrieben bei jeder bestätigten Aufgabe. Unabhängig von den Aufgaben selbst, damit
// "Erledigte löschen" den Fortschritt nicht zurücksetzt.
@Document("achievementProgress")
public record AchievementProgress(
        @Id String id,
        @Indexed(unique = true) String memberId,
        int tasksTotal,
        Map<TaskCategory, Integer> tasksByCategory,
        int pointsEarned,
        LocalDate lastTaskDay,
        int currentStreak,
        int bestStreak) {

    public AchievementProgress {
        tasksByCategory = tasksByCategory == null ? Map.of() : Map.copyOf(tasksByCategory);
    }

    public static AchievementProgress empty(String memberId) {
        return new AchievementProgress(null, memberId, 0, Map.of(), 0, null, 0, 0);
    }

    // Serie: am selben Tag bleibt sie, am Folgetag wächst sie, nach einer Lücke beginnt sie neu.
    public AchievementProgress withConfirmedTask(Task task, LocalDate day) {
        Map<TaskCategory, Integer> categories = new EnumMap<>(TaskCategory.class);
        categories.putAll(tasksByCategory);
        categories.merge(task.category(), 1, Integer::sum);
        int streak = day.equals(lastTaskDay) ? currentStreak
                : lastTaskDay != null && day.equals(lastTaskDay.plusDays(1)) ? currentStreak + 1 : 1;
        return new AchievementProgress(id, memberId, tasksTotal + 1, categories, pointsEarned + task.points(), day,
                streak, Math.max(bestStreak, streak));
    }

    public int valueFor(Achievement achievement) {
        return switch (achievement.rule()) {
            case TASKS_TOTAL -> tasksTotal;
            case TASKS_IN_CATEGORY -> tasksByCategory.getOrDefault(achievement.category(), 0);
            case STREAK_DAYS -> bestStreak;
            case POINTS_EARNED -> pointsEarned;
        };
    }
}
