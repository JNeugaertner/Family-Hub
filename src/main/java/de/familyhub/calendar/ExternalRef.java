package de.familyhub.calendar;

// Herkunft eines importierten Termins, z. B. aus dem Google Kalender. Importierte Termine sind in FamilyHub
// schreibgeschützt und werden beim Abgleich über calendarId + eventId wiedererkannt.
public record ExternalRef(String provider, String calendarId, String eventId) {

    public static final String GOOGLE = "google";

    public String key() {
        return calendarId + "/" + eventId;
    }
}
