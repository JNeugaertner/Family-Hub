package de.familyhub.calendar;

import java.time.LocalDate;

import io.swagger.v3.oas.annotations.media.Schema;

// Zugehörigkeit eines Termins zu einer Serie: die Serie, der Tag, für den er nach der Regel angelegt wurde (bleibt,
// auch wenn der Termin verschoben wird), und ob er einzeln geändert wurde. Einzeln geänderte Termine lässt eine
// Änderung der Serie in Ruhe.
public record SeriesRef(
        @Schema(description = "Id der Serie") String seriesId,
        @Schema(description = "Tag des Termins laut Regel, auch wenn er einzeln verschoben wurde") LocalDate date,
        @Schema(description = "Einzeln geändert; Änderungen an der Serie übernimmt er nicht") boolean changed) {

    public SeriesRef asChanged() {
        return new SeriesRef(seriesId, date, true);
    }
}
