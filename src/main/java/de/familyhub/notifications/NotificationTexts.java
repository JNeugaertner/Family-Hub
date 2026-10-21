package de.familyhub.notifications;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

// Bausteine für die Texte der Benachrichtigungen (reiner Text, Telegram formatiert nichts)
final class NotificationTexts {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    private NotificationTexts() {
    }

    static String quoted(String title) {
        return "„" + title + "“";
    }

    // "heute", "morgen" oder "am 24.10."
    static String day(LocalDate date, LocalDate today) {
        if (date.equals(today)) {
            return "heute";
        }
        return date.equals(today.plusDays(1)) ? "morgen" : "am " + date.format(DATE);
    }

    // "heute um 15:30"
    static String dayAndTime(LocalDateTime at, LocalDate today) {
        return day(at.toLocalDate(), today) + " um " + at.format(TIME);
    }

    // "24 Stunden", "einer Stunde" oder "30 Minuten", passend zu "seit über …"
    static String duration(Duration duration) {
        long hours = duration.toHours();
        if (hours == 0) {
            return duration.toMinutes() + " Minuten";
        }
        return hours == 1 ? "einer Stunde" : hours + " Stunden";
    }

    // Ein Eintrag hinter der Überschrift, mehrere als Aufzählung darunter
    static String list(String heading, List<String> lines) {
        return lines.size() == 1 ? heading + " " + lines.getFirst() : heading + "\n• " + String.join("\n• ", lines);
    }
}
