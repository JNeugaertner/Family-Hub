package de.familyhub.telegram;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

// Verknüpfung eines Familienmitglieds mit seinem Telegram-Konto: Die Person hat den Bot mit einem Einmal-Code aus
// FamilyHub gestartet (TelegramLinkCodes). Jede Person hat höchstens ein Konto, jedes Konto gehört zu höchstens einer
// Person. chatId ist der private Chat mit dem Bot, dorthin schickt FamilyHub Nachrichten.
@Document("telegramLinks")
public record TelegramLink(
        @Id String id,
        @Indexed(unique = true) String memberId,
        @Indexed(unique = true) long telegramUserId,
        long chatId,
        // Name in Telegram, damit die Oberfläche zeigt, welches Konto verbunden ist
        String telegramName,
        Instant linkedAt) {
}
