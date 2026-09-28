package de.familyhub.achievements;

import static de.familyhub.achievements.AchievementRule.POINTS_EARNED;
import static de.familyhub.achievements.AchievementRule.STREAK_DAYS;
import static de.familyhub.achievements.AchievementRule.TASKS_IN_CATEGORY;
import static de.familyhub.achievements.AchievementRule.TASKS_TOTAL;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import de.familyhub.task.TaskCategory;

// Fester Katalog der Erfolge. Fehlende Einträge werden beim Start ergänzt (über den Schlüssel), bestehende
// bleiben unverändert, damit Anpassungen der Eltern (aktiv, Ziel, Bonus) erhalten bleiben.
@Component
@ConditionalOnProperty(name = "familyhub.achievements.default-catalog", havingValue = "true", matchIfMissing = true)
public class AchievementCatalog implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AchievementCatalog.class);

    static final List<Achievement> DEFAULTS = List.of(
            entry("tasks-1", "🌱", "Erste Aufgabe", "Die erste Aufgabe erledigt", TASKS_TOTAL, null, 1, 10),
            entry("tasks-10", "💪", "Fleißig", "10 Aufgaben erledigt", TASKS_TOTAL, null, 10, 50),
            entry("tasks-50", "🏅", "Aufgaben-Profi", "50 Aufgaben erledigt", TASKS_TOTAL, null, 50, 150),
            entry("school-5", "📚", "Schlaukopf", "5 Schulaufgaben erledigt", TASKS_IN_CATEGORY, TaskCategory.SCHOOL, 5, 50),
            entry("chores-10", "🧹", "Ordnungsprofi", "10 Haushaltsaufgaben erledigt", TASKS_IN_CATEGORY,
                    TaskCategory.CHORES, 10, 50),
            entry("home-5", "🏠", "Haushaltsheld", "5 Aufgaben rund ums Haus erledigt", TASKS_IN_CATEGORY,
                    TaskCategory.HOME, 5, 40),
            entry("streak-3", "🔥", "Dranbleiber", "3 Tage hintereinander eine Aufgabe erledigt", STREAK_DAYS, null, 3, 30),
            entry("streak-7", "⚡", "Wochenserie", "7 Tage hintereinander eine Aufgabe erledigt", STREAK_DAYS, null, 7, 100),
            entry("points-200", "⭐", "Sammler", "200 Punkte mit Aufgaben verdient", POINTS_EARNED, null, 200, 50),
            entry("points-1000", "🏆", "Punkte-Champion", "1000 Punkte mit Aufgaben verdient", POINTS_EARNED, null, 1000,
                    200));

    private final AchievementRepository achievements;

    public AchievementCatalog(AchievementRepository achievements) {
        this.achievements = achievements;
    }

    private static Achievement entry(String key, String icon, String name, String description, AchievementRule rule,
            TaskCategory category, int target, int bonus) {
        return new Achievement(null, key, icon, name, description, rule, category, target, bonus, true);
    }

    @Override
    public void run(ApplicationArguments args) {
        List<Achievement> missing = DEFAULTS.stream().filter(a -> achievements.findByKey(a.key()).isEmpty()).toList();
        if (!missing.isEmpty()) {
            achievements.saveAll(missing);
            log.info("Erfolge-Katalog ergänzt: {} Erfolge.", missing.size());
        }
    }
}
