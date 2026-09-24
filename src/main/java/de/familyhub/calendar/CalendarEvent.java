package de.familyhub.calendar;

import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.mongodb.core.mapping.Document;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

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
        String description,

        @JsonProperty("private")
        @Schema(description = "Privat: nur für die Person selbst, wer ihn angelegt hat, und Administratoren sichtbar")
        Boolean privateEvent,

        @Schema(accessMode = Schema.AccessMode.READ_ONLY,
                description = "approved oder proposed (Vorschlag, wartet auf Freigabe durch einen Administrator)")
        EventStatus status,

        @Schema(accessMode = Schema.AccessMode.READ_ONLY, description = "Id des Mitglieds, das den Termin angelegt hat")
        String createdBy) {

    @PersistenceCreator
    public CalendarEvent {
        privateEvent = Boolean.TRUE.equals(privateEvent);
        status = status == null ? EventStatus.APPROVED : status;
    }

    public CalendarEvent(String id, String title, LocalDateTime start, LocalDateTime end, String memberId,
            EventCategory category, String location, String description) {
        this(id, title, start, end, memberId, category, location, description, false, EventStatus.APPROVED, null);
    }

    @JsonIgnore
    public boolean isProposal() {
        return status == EventStatus.PROPOSED;
    }

    // Fehlende Zeiten meldet bereits @NotNull, daher hier nur die Reihenfolge prüfen.
    @JsonIgnore
    @AssertTrue(message = "Ende muss nach dem Beginn liegen")
    public boolean isEndAfterStart() {
        return start == null || end == null || end.isAfter(start);
    }
}
