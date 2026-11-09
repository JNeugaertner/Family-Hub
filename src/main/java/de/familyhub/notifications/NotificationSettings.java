package de.familyhub.notifications;

import java.util.Set;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.mongodb.core.mapping.Document;

// Welche Benachrichtigungen eine Person abgeschaltet hat. Gespeichert wird, was aus ist: Ohne Eintrag und für
// später hinzukommende Arten ist alles an.
@Document("notificationSettings")
public record NotificationSettings(@Id String memberId, Set<NotificationKind> muted) {

    @PersistenceCreator
    public NotificationSettings {
        muted = muted == null ? Set.of() : Set.copyOf(muted);
    }
}
