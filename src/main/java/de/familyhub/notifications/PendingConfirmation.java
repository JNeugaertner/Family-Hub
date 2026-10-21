package de.familyhub.notifications;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

// Seit wann FamilyHub einen offenen Punkt kennt (key z. B. "task:<id>") und ob schon daran erinnert wurde
@Document("pendingConfirmations")
public record PendingConfirmation(@Id String key, Instant since, boolean reminded) {

    PendingConfirmation remindedNow() {
        return new PendingConfirmation(key, since, true);
    }
}
