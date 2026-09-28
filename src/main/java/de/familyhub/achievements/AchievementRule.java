package de.familyhub.achievements;

import com.fasterxml.jackson.annotation.JsonProperty;

// Wonach ein Erfolg bemessen wird. Gezählt wird ab Einführung der Erfolge (28.09.2026), nicht rückwirkend.
public enum AchievementRule {
    // Anzahl bestätigter Aufgaben
    @JsonProperty("tasks") TASKS_TOTAL,
    // Anzahl bestätigter Aufgaben einer Kategorie
    @JsonProperty("category") TASKS_IN_CATEGORY,
    // Tage hintereinander mit mindestens einer bestätigten Aufgabe (längste Serie)
    @JsonProperty("streak") STREAK_DAYS,
    // Mit Aufgaben verdiente Punkte (ohne Boni, Einlösungen zählen nicht dagegen)
    @JsonProperty("points") POINTS_EARNED
}
