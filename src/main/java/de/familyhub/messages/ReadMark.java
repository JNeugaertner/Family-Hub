package de.familyhub.messages;

import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

// Bis wann eine Person eine Unterhaltung gelesen hat; spätere Nachrichten anderer zählen als ungelesen.
@Document("messageReads")
public record ReadMark(@Id String id, String memberId, String conversation, LocalDateTime readAt) {

    public static ReadMark of(String memberId, String conversation, LocalDateTime readAt) {
        return new ReadMark(idFor(memberId, conversation), memberId, conversation, readAt);
    }

    static String idFor(String memberId, String conversation) {
        return memberId + "|" + conversation;
    }
}
