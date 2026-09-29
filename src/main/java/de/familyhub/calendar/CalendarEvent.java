package de.familyhub.calendar;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.mongodb.core.mapping.Document;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
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

        @NotEmpty(message = "Termin muss mindestens einem Familienmitglied zugeordnet sein")
        @Schema(description = "Beteiligte Familienmitglieder, mindestens eines")
        List<String> memberIds,

        @NotNull(message = "Kategorie ist Pflicht")
        EventCategory category,

        @Size(max = 200, message = "Ort darf höchstens 200 Zeichen lang sein")
        String location,

        @Size(max = 1000, message = "Beschreibung darf höchstens 1000 Zeichen lang sein")
        String description,

        @JsonProperty("private")
        @Schema(description = "Privat: nur für die Beteiligten, wer ihn angelegt hat, und Administratoren sichtbar")
        Boolean privateEvent,

        @Schema(accessMode = Schema.AccessMode.READ_ONLY,
                description = "approved oder proposed (Vorschlag, wartet auf Freigabe durch einen Administrator)")
        EventStatus status,

        @Schema(accessMode = Schema.AccessMode.READ_ONLY, description = "Id des Mitglieds, das den Termin angelegt hat")
        String createdBy,

        @Schema(accessMode = Schema.AccessMode.READ_ONLY,
                description = "Nur bei importierten Terminen (z. B. Google Kalender); diese sind schreibgeschützt")
        ExternalRef external) {

    @PersistenceCreator
    public CalendarEvent {
        // doppelte und leere Einträge fallen weg, die Reihenfolge bleibt
        memberIds = memberIds == null ? List.of()
                : memberIds.stream().filter(Objects::nonNull).filter(m -> !m.isBlank()).distinct().toList();
        privateEvent = Boolean.TRUE.equals(privateEvent);
        status = status == null ? EventStatus.APPROVED : status;
    }

    public CalendarEvent(String id, String title, LocalDateTime start, LocalDateTime end, String memberId,
            EventCategory category, String location, String description, Boolean privateEvent, EventStatus status,
            String createdBy) {
        this(id, title, start, end, List.of(memberId), category, location, description, privateEvent, status, createdBy,
                null);
    }

    public CalendarEvent(String id, String title, LocalDateTime start, LocalDateTime end, String memberId,
            EventCategory category, String location, String description) {
        this(id, title, start, end, memberId, category, location, description, false, EventStatus.APPROVED, null);
    }

    @JsonIgnore
    public boolean involves(String memberId) {
        return memberIds.contains(memberId);
    }

    // Termin nur dieser einen Person (zählt für Rechte im Geltungsbereich "eigen")
    @JsonIgnore
    public boolean isOnlyFor(String memberId) {
        return memberIds.size() == 1 && memberIds.get(0).equals(memberId);
    }

    public CalendarEvent withoutMember(String memberId) {
        return new CalendarEvent(id, title, start, end, memberIds.stream().filter(m -> !m.equals(memberId)).toList(),
                category, location, description, privateEvent, status, createdBy, external);
    }

    @JsonIgnore
    public boolean isProposal() {
        return status == EventStatus.PROPOSED;
    }

    @JsonIgnore
    public boolean isExternal() {
        return external != null;
    }

    // Fehlende Zeiten meldet bereits @NotNull, daher hier nur die Reihenfolge prüfen.
    @JsonIgnore
    @AssertTrue(message = "Ende muss nach dem Beginn liegen")
    public boolean isEndAfterStart() {
        return start == null || end == null || end.isAfter(start);
    }
}
