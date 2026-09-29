package de.familyhub.sampledata;

import static de.familyhub.task.TaskCategory.CHORES;
import static de.familyhub.task.TaskCategory.ERRANDS;
import static de.familyhub.task.TaskCategory.FAMILY;
import static de.familyhub.task.TaskCategory.HEALTH;
import static de.familyhub.task.TaskCategory.HOME;
import static de.familyhub.task.TaskCategory.SCHOOL;
import static de.familyhub.task.TaskPriority.HIGH;
import static de.familyhub.task.TaskPriority.LOW;
import static de.familyhub.task.TaskPriority.MEDIUM;
import static de.familyhub.task.TaskStatus.CONFIRMED;
import static de.familyhub.task.TaskStatus.DONE;
import static de.familyhub.task.TaskStatus.IN_PROGRESS;
import static de.familyhub.task.TaskStatus.TODO;

import java.util.List;

import de.familyhub.task.TaskCategory;
import de.familyhub.task.TaskPriority;
import de.familyhub.task.TaskStatus;

// Beispielaufgaben (ursprünglich aus dem Figma-UI) und eine Punkte-Historie, die die
// Punktestände aus dem Figma-UI ergibt (Emma 420, Lucas 285, Lily 190).
final class SampleTasks {

    // day: Fälligkeit relativ zu heute; username: zugewiesenes Mitglied der Beispielfamilie
    record SampleTask(String title, String description, String username, int day, TaskPriority priority,
            TaskCategory category, int points, TaskStatus status) {
    }

    static final List<SampleTask> TASKS = List.of(
            new SampleTask("Zimmer aufräumen", "Aufräumen, staubsaugen und Staub wischen", "emma", 1, MEDIUM, CHORES, 20, TODO),
            new SampleTask("Müll rausbringen", "Tonnen bis 8 Uhr an die Straße stellen", "lucas", 0, HIGH, CHORES, 15, TODO),
            new SampleTask("Mathe-Hausaufgaben", "Kapitel 5, Aufgaben 1–20", "lucas", 0, HIGH, SCHOOL, 25, IN_PROGRESS),
            new SampleTask("Zahnarzttermin für Lucas ausmachen", null, "sarah", -1, HIGH, HEALTH, 0, DONE),
            new SampleTask("Wocheneinkauf", "Alles von der Einkaufsliste besorgen", "mike", 0, MEDIUM, ERRANDS, 0,
                    IN_PROGRESS),
            new SampleTask("Hund füttern", null, "lily", 0, HIGH, CHORES, 10, CONFIRMED),
            new SampleTask("Blumen gießen", null, "emma", 2, LOW, CHORES, 10, TODO),
            new SampleTask("30 Minuten lesen", null, "lily", 0, MEDIUM, SCHOOL, 15, IN_PROGRESS),
            new SampleTask("Wochenende planen", null, "sarah", 3, LOW, FAMILY, 0, TODO),
            new SampleTask("Licht in der Garage reparieren", null, "mike", 4, MEDIUM, HOME, 0, TODO),
            new SampleTask("Klavier üben", null, "lily", 0, MEDIUM, SCHOOL, 20, DONE),
            new SampleTask("Wertstofftonnen aufstellen", null, "mike", 1, LOW, CHORES, 0, DONE));

    // Frühere Gutschriften; die bestätigte Aufgabe "Hund füttern" kommt als eigene Buchung dazu.
    record SamplePoints(String username, int daysAgo, int amount, String reason) {
    }

    static final List<SamplePoints> POINTS = List.of(
            new SamplePoints("emma", 21, 300, "Punkte aus dem letzten Monat"),
            new SamplePoints("emma", 10, 40, "Auf Lily aufgepasst"),
            new SamplePoints("emma", 5, 60, "Im Garten geholfen"),
            new SamplePoints("emma", 2, 20, "Zimmer aufgeräumt"),
            new SamplePoints("lucas", 21, 200, "Punkte aus dem letzten Monat"),
            new SamplePoints("lucas", 9, 25, "Mathe-Hausaufgaben"),
            new SamplePoints("lucas", 6, 20, "Müll rausgebracht"),
            new SamplePoints("lucas", 3, 40, "Auto gewaschen"),
            new SamplePoints("lily", 21, 120, "Punkte aus dem letzten Monat"),
            new SamplePoints("lily", 8, 20, "Klavier geübt"),
            new SamplePoints("lily", 4, 40, "Zimmer aufgeräumt"));

    private SampleTasks() {
    }
}
