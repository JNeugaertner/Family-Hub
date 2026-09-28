package de.familyhub.google;

import java.time.LocalDate;
import java.time.OffsetDateTime;

// Ein Termin, wie ihn die Google Calendar API liefert (nur die Felder, die FamilyHub braucht).
// Ganztägige Termine haben date statt dateTime; das Ende ist dann exklusiv (Folgetag).
public record GoogleEvent(
        String id,
        String status,
        String summary,
        String description,
        String location,
        String visibility,
        Time start,
        Time end) {

    public record Time(OffsetDateTime dateTime, LocalDate date) {
    }
}
