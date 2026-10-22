package de.familyhub.messages;

import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import io.swagger.v3.oas.annotations.media.Schema;

// Eine Nachricht in der Familiengruppe oder in einem Einzelchat (siehe Conversations).
@Document("messages")
@CompoundIndex(name = "conversation_sentAt", def = "{'conversation': 1, 'sentAt': -1}")
public record ChatMessage(
        @Id String id,
        @Schema(description = "family oder direct:<id>:<id>") String conversation,
        String senderId,
        String text,
        LocalDateTime sentAt,
        @Schema(description = "Über einen Entwurf des KI-Assistenten geschrieben; abgeschickt hat sie der Absender")
        Boolean viaAssistant) {

    @PersistenceCreator
    public ChatMessage {
        viaAssistant = Boolean.TRUE.equals(viaAssistant);
    }

    public ChatMessage(String id, String conversation, String senderId, String text, LocalDateTime sentAt) {
        this(id, conversation, senderId, text, sentAt, false);
    }
}
