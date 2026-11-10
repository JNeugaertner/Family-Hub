package de.familyhub.calendar;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.mongodb.core.mapping.Document;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

// Vorlage für Termine, die sich mit gleichem Ort und gleicher Uhrzeit wiederholen, aber nicht regelmäßig
// (z. B. "Zahnarzt Dr. Weber, 08:30, 45 Minuten"). Im Termin-Formular füllt sie die Felder aus; der Termin selbst
// wird ganz normal angelegt und geprüft. Die Vorlage sehen alle, die Termine anlegen oder vorschlagen dürfen;
// private Vorlagen nur, wer sie angelegt hat.
@Document("event_templates")
public record EventTemplate(
        @Id @Schema(accessMode = Schema.AccessMode.READ_ONLY) String id,

        @NotBlank(message = "Name der Vorlage darf nicht leer sein")
        @Size(max = 60, message = "Name der Vorlage darf höchstens 60 Zeichen lang sein")
        @Schema(description = "Name in der Auswahl, z. B. \"Zahnarzt Emma\"")
        String name,

        @NotBlank(message = "Titel darf nicht leer sein")
        @Size(max = 100, message = "Titel darf höchstens 100 Zeichen lang sein")
        String title,

        // Als Text "HH:mm", nicht LocalTime: MongoDB speicherte LocalTime als Datum mit Zeitzone
        @NotNull(message = "Uhrzeit ist Pflicht")
        @Pattern(regexp = "([01]\\d|2[0-3]):[0-5]\\d", message = "Uhrzeit im Format HH:mm, z. B. 08:30")
        @Schema(example = "08:30", description = "Beginn (Uhrzeit, ohne Datum)")
        String startTime,

        @NotNull(message = "Dauer ist Pflicht")
        @Min(value = 5, message = "Dauer muss mindestens 5 Minuten sein")
        @Max(value = 1440, message = "Dauer darf höchstens 24 Stunden sein")
        @Schema(description = "Dauer in Minuten")
        Integer durationMinutes,

        @Schema(description = "Beteiligte; beim Übernehmen bleiben nur die, die man eintragen darf")
        List<String> memberIds,

        @NotNull(message = "Kategorie ist Pflicht")
        EventCategory category,

        @Size(max = 200, message = "Ort darf höchstens 200 Zeichen lang sein")
        String location,

        @DecimalMin(value = "-90", message = "Ungültiger Breitengrad")
        @DecimalMax(value = "90", message = "Ungültiger Breitengrad")
        Double lat,

        @DecimalMin(value = "-180", message = "Ungültiger Längengrad")
        @DecimalMax(value = "180", message = "Ungültiger Längengrad")
        Double lon,

        @Pattern(regexp = "driving-car|cycling-regular|foot-walking", message = "Unbekanntes Verkehrsmittel")
        String transportMode,

        @Schema(description = "Fahrzeit vom Wohnort in Sekunden, wie beim Termin")
        Double travelTime,

        @Size(max = 1000, message = "Beschreibung darf höchstens 1000 Zeichen lang sein")
        String description,

        @JsonProperty("private")
        @Schema(description = "Privat: Vorlage sieht nur, wer sie angelegt hat; Termine daraus sind privat")
        Boolean privateTemplate,

        @Schema(accessMode = Schema.AccessMode.READ_ONLY) String createdBy,
        @Schema(accessMode = Schema.AccessMode.READ_ONLY) LocalDateTime createdAt) {

    @PersistenceCreator
    public EventTemplate {
        memberIds = memberIds == null ? List.of()
                : memberIds.stream().filter(Objects::nonNull).filter(m -> !m.isBlank()).distinct().toList();
        privateTemplate = Boolean.TRUE.equals(privateTemplate);
    }

    // Inhalt aus der Anfrage übernehmen; Id, wer sie angelegt hat und wann, bleiben
    EventTemplate withContent(EventTemplate changed) {
        return new EventTemplate(id, changed.name().strip(), changed.title().strip(), changed.startTime(),
                changed.durationMinutes(), changed.memberIds(), changed.category(), blankToNull(changed.location()),
                changed.lat(), changed.lon(), changed.transportMode(), changed.travelTime(),
                blankToNull(changed.description()), changed.privateTemplate(), createdBy, createdAt);
    }

    EventTemplate createdBy(String memberId, LocalDateTime at) {
        return new EventTemplate(null, null, null, null, null, null, null, null, null, null, null, null, null, null,
                memberId, at).withContent(this);
    }

    private static String blankToNull(String text) {
        return text == null || text.isBlank() ? null : text.strip();
    }
}
