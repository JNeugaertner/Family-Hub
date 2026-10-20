package de.familyhub.google;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonProperty;

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
        Time end,
        ExtendedProperties extendedProperties) {

    // Unter diesem Schlüssel steht an Terminen, die FamilyHub nach Google einträgt, die Id des FamilyHub-Termins
    static final String FAMILYHUB_ID = "familyhubId";

    public GoogleEvent(String id, String status, String summary, String description, String location,
            String visibility, Time start, Time end) {
        this(id, status, summary, description, location, visibility, start, end, null);
    }

    public record Time(OffsetDateTime dateTime, LocalDate date) {
    }

    // privateProperties: nur an der Kopie des Termins in diesem Kalender sichtbar, nicht bei Gästen
    public record ExtendedProperties(@JsonProperty("private") Map<String, String> privateProperties) {
    }

    // Id des FamilyHub-Termins, wenn FamilyHub diesen Termin nach Google eingetragen hat, sonst null
    public String familyhubId() {
        return extendedProperties == null || extendedProperties.privateProperties() == null ? null
                : extendedProperties.privateProperties().get(FAMILYHUB_ID);
    }
}
