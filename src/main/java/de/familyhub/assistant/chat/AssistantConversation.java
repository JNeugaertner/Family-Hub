package de.familyhub.assistant.chat;

import java.time.Instant;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

// Der Chat-Verlauf einer Person mit dem Assistenten (Entscheidung vom 22.10.2026: pro Person gespeichert, auch über
// Geräte hinweg). MongoDB löscht ihn 30 Tage nach der letzten Nachricht von selbst.
@Document("assistantConversations")
public record AssistantConversation(
        @Id String memberId,
        List<ChatTurn> turns,
        @Indexed(expireAfter = "30d") Instant updatedAt) {

    @PersistenceCreator
    public AssistantConversation {
        turns = turns == null ? List.of() : List.copyOf(turns);
    }

    static AssistantConversation empty(String memberId) {
        return new AssistantConversation(memberId, List.of(), null);
    }
}
