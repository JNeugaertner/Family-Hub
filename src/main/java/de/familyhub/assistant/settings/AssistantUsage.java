package de.familyhub.assistant.settings;

import java.time.Instant;
import java.time.LocalDate;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

// Wie viele Fragen eine Person an einem Tag gestellt hat (für das Tageslimit und die Admin-Karte); id ist
// "<memberId>:<Tag>". MongoDB löscht die Zähler nach 8 Tagen, die Admin-Karte zeigt 7.
@Document("assistantUsage")
public record AssistantUsage(
        @Id String id,
        @Indexed String memberId,
        LocalDate day,
        int questions,
        @Indexed(expireAfter = "8d") Instant createdAt) {
}
