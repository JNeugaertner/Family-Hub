package de.familyhub.calendar;

import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@Document("events")
public record CalendarEvent(
        @Id @Schema(accessMode = Schema.AccessMode.READ_ONLY) String id,

        @NotBlank(message = "Titel darf nicht leer sein")
        @Size(max = 100, message = "Titel darf höchstens 100 Zeichen lang sein")
        String title,

        @NotNull(message = "Beginn ist Pflicht")
        LocalDateTime start,

        @NotNull(message = "Ende ist Pflicht")
        LocalDateTime end,

        @NotBlank(message = "Termin muss einem Familienmitglied zugeordnet sein")
        String memberId,

        @NotNull(message = "Kategorie ist Pflicht")
        EventCategory category,

        @Size(max = 200, message = "Ort darf höchstens 200 Zeichen lang sein")
        String location,

        @Size(max = 1000, message = "Beschreibung darf höchstens 1000 Zeichen lang sein")
        String description) {

    // Fehlende Zeiten meldet bereits @NotNull, daher hier nur die Reihenfolge prüfen.
    @JsonIgnore
    @AssertTrue(message = "Ende muss nach dem Beginn liegen")
    public boolean isEndAfterStart() {
        return start == null || end == null || end.isAfter(start);
    }
}
