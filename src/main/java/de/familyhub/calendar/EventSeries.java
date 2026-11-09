package de.familyhub.calendar;

import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

// Terminserie (Entscheidung vom 03.11.2026): Die Vorlage ist der erste Termin samt Regel (template.recurrence).
// Die einzelnen Termine legt EventSeriesService als normale Termine an, so weit im Voraus wie
// familyhub.calendar.series-horizon. Einzeln gelöschte Tage stehen in skippedDates, damit sie nicht wiederkommen;
// generatedUntil ist der letzte Tag, bis zu dem die Termine schon angelegt sind.
@Document("eventSeries")
public record EventSeries(@Id String id, CalendarEvent template, Set<LocalDate> skippedDates, LocalDate generatedUntil) {

    public EventSeries {
        skippedDates = skippedDates == null ? Set.of() : Set.copyOf(skippedDates);
    }

    public LocalDate firstDay() {
        return template.start().toLocalDate();
    }

    public Recurrence recurrence() {
        return template.recurrence();
    }

    public EventSeries withTemplate(CalendarEvent newTemplate) {
        return new EventSeries(id, newTemplate, skippedDates, generatedUntil);
    }

    public EventSeries withGeneratedUntil(LocalDate day) {
        return new EventSeries(id, template, skippedDates, day);
    }

    public EventSeries skipping(LocalDate day) {
        Set<LocalDate> skipped = new HashSet<>(skippedDates);
        skipped.add(day);
        return new EventSeries(id, template, skipped, generatedUntil);
    }

    // Die Serie endet vor day; ausgelassene Tage ab day gehören dann zu keiner Regel mehr
    public EventSeries endingBefore(LocalDate day) {
        return new EventSeries(id, template.withSeries(null, recurrence().endingBefore(day), null),
                skippedFrom(LocalDate.MIN, day), generatedUntil);
    }

    // Ausgelassene Tage in [from, to)
    public Set<LocalDate> skippedFrom(LocalDate from, LocalDate to) {
        return skippedDates.stream().filter(day -> !day.isBefore(from) && day.isBefore(to)).collect(Collectors.toSet());
    }
}
