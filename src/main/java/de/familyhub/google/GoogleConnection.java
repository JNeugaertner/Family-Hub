package de.familyhub.google;

import java.time.Instant;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

// Verbindung eines Familienmitglieds mit seinem Google-Konto. Der Refresh-Token ist verschlüsselt (TokenCipher)
// und verlässt das Backend nie.
@Document("googleConnections")
public record GoogleConnection(
        @Id String id,
        @Indexed(unique = true) String memberId,
        String googleEmail,
        String encryptedRefreshToken,
        Instant connectedAt,
        List<GoogleCalendarChoice> calendars,
        Instant lastSyncAt,
        String lastSyncError,
        boolean needsReconnect) {

    public GoogleConnection {
        calendars = calendars == null ? List.of() : List.copyOf(calendars);
    }

    public GoogleConnection withCalendars(List<GoogleCalendarChoice> newCalendars) {
        return new GoogleConnection(id, memberId, googleEmail, encryptedRefreshToken, connectedAt, newCalendars,
                lastSyncAt, lastSyncError, needsReconnect);
    }

    public GoogleConnection withSyncResult(Instant syncedAt, String error, boolean reconnect) {
        return new GoogleConnection(id, memberId, googleEmail, encryptedRefreshToken, connectedAt, calendars,
                syncedAt, error, reconnect);
    }
}
