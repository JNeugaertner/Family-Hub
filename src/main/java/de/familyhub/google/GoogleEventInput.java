package de.familyhub.google;

import java.time.LocalDate;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

// Ein Termin, wie FamilyHub ihn nach Google schreibt (Anlegen und Ändern). Leere Felder gehen nicht mit; beim Ändern
// ersetzt Google den ganzen Termin, leere Felder sind danach also auch dort leer.
@JsonInclude(JsonInclude.Include.NON_NULL)
public record GoogleEventInput(
        String summary,
        String description,
        String location,
        // "private" oder "default"
        String visibility,
        Time start,
        Time end,
        ExtendedProperties extendedProperties) {

    // dateTime mit Versatz, z. B. 2026-10-01T10:00:00+02:00, und timeZone z. B. Europe/Berlin; bei ganztägigen
    // Terminen stattdessen nur date (yyyy-MM-dd), das Ende ist dann exklusiv (Folgetag)
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Time(String dateTime, String date, String timeZone) {

        public Time(String dateTime, String timeZone) {
            this(dateTime, null, timeZone);
        }

        public static Time allDay(LocalDate date) {
            return new Time(null, date.toString(), null);
        }
    }

    public record ExtendedProperties(@JsonProperty("private") Map<String, String> privateProperties) {
    }
}
