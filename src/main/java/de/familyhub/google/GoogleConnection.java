package de.familyhub.google;

import java.time.Instant;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

// Verbindung eines Familienmitglieds mit seinem Google-Konto. Der Refresh-Token ist verschlüsselt (TokenCipher)
// und verlässt das Backend nie.
// writeGranted: Google hat erlaubt, den Kalender „FamilyHub“ anzulegen und dort Termine einzutragen.
// exportCalendarId: Id dieses Kalenders in Google; gesetzt, solange FamilyHub-Termine nach Google eingetragen werden.
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
        boolean needsReconnect,
        boolean writeGranted,
        String exportCalendarId) {

    public GoogleConnection {
        calendars = calendars == null ? List.of() : List.copyOf(calendars);
    }

    public boolean exportEnabled() {
        return exportCalendarId != null;
    }

    public GoogleConnection withCalendars(List<GoogleCalendarChoice> newCalendars) {
        return new GoogleConnection(id, memberId, googleEmail, encryptedRefreshToken, connectedAt, newCalendars,
                lastSyncAt, lastSyncError, needsReconnect, writeGranted, exportCalendarId);
    }

    public GoogleConnection withSyncResult(Instant syncedAt, String error, boolean reconnect) {
        return new GoogleConnection(id, memberId, googleEmail, encryptedRefreshToken, connectedAt, calendars,
                syncedAt, error, reconnect, writeGranted, exportCalendarId);
    }

    public GoogleConnection withExportCalendar(String calendarId) {
        return new GoogleConnection(id, memberId, googleEmail, encryptedRefreshToken, connectedAt, calendars,
                lastSyncAt, lastSyncError, needsReconnect, writeGranted, calendarId);
    }
}
