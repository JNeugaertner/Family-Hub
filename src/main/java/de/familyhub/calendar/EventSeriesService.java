package de.familyhub.calendar;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import de.familyhub.web.ApiException;

// Terminserien (Entscheidung vom 03.11.2026): Eine Serie speichert die Regel; ihre Termine sind normale Termine mit
// series-Verweis, angelegt bis familyhub.calendar.series-horizon im Voraus und nachts verlängert. So funktionieren
// Rechte, Benachrichtigungen, Google-Export und Ansichten für Serientermine ohne Sonderfälle.
// Ein Vorschlag (Jugendliche für andere) besteht nur aus dem ersten Termin; die übrigen entstehen mit der Freigabe.
// Einzeln geänderte Termine (series.changed) lassen Änderungen an der Serie in Ruhe; einzeln gelöschte Tage merkt
// sich die Serie, damit sie nicht wiederkommen.
@Service
public class EventSeriesService {

    private static final Logger log = LoggerFactory.getLogger(EventSeriesService.class);
    private static final Comparator<CalendarEvent> BY_START = Comparator.comparing(CalendarEvent::start);

    private final CalendarEventRepository events;
    private final EventSeriesRepository series;
    private final CalendarProperties properties;
    private final Clock clock;

    public EventSeriesService(CalendarEventRepository events, EventSeriesRepository series,
            CalendarProperties properties, Clock clock) {
        this.events = events;
        this.series = series;
        this.properties = properties;
        this.clock = clock;
    }

    // template: der erste Termin samt Regel, ohne id. Gibt die angelegten Termine nach Beginn sortiert zurück.
    public List<CalendarEvent> create(CalendarEvent template) {
        requireSomeDate(template, Set.of());
        EventSeries created = series.save(new EventSeries(null, template.withSeries(null, template.recurrence(), null),
                Set.of(), null));
        return generate(created, created.firstDay());
    }

    // Alle Termine der Serie ab diesem Termin (für Rechteprüfungen vor dem Löschen)
    public List<CalendarEvent> following(CalendarEvent occurrence) {
        LocalDate day = occurrence.series().date();
        return events.findBySeries(occurrence.series().seriesId()).stream()
                .filter(event -> !event.series().date().isBefore(day)).toList();
    }

    // "Diesen und alle folgenden": Ab dem Tag dieses Termins gilt changed, mit Regel als (neue) Serie, ohne Regel als
    // Einzeltermin. Einzeln geänderte spätere Termine bleiben, wie sie sind. Gibt die Termine ab hier zurück.
    public List<CalendarEvent> changeFollowing(CalendarEvent occurrence, CalendarEvent changed) {
        EventSeries current = find(occurrence);
        LocalDate day = occurrence.series().date();
        List<CalendarEvent> all = events.findBySeries(current.id());
        boolean whole = all.stream().noneMatch(event -> event.series().date().isBefore(day));
        Recurrence rule = changed.recurrence();
        if (rule != null && !whole && rule.equals(current.recurrence()) && rule.count() != null) {
            // Unveränderte Regel mit Anzahl: Die Serie soll insgesamt nicht länger werden
            int before = current.recurrence().dates(current.firstDay(), current.firstDay(), day.minusDays(1)).size();
            rule = rule.withCount(rule.count() - before);
        }
        CalendarEvent template = changed.withSeries(null, rule, null);
        Set<LocalDate> skipped = whole ? current.skippedDates() : current.skippedFrom(day, LocalDate.MAX);
        if (rule != null) {
            // Vor dem ersten Löschen prüfen, damit bei einer unbrauchbaren Regel alles bleibt, wie es war
            requireSomeDate(template, skipped);
        }

        List<CalendarEvent> following = all.stream().filter(event -> !event.series().date().isBefore(day)).toList();
        List<CalendarEvent> keptChanged = following.stream()
                .filter(event -> event.series().changed() && !event.id().equals(occurrence.id())).toList();
        events.deleteAll(following.stream().filter(event -> !keptChanged.contains(event)).toList());

        if (rule == null) {
            // Ab hier keine Wiederholung mehr: Einzeln geänderte Termine gehören dann zu keiner Serie
            events.saveAll(keptChanged.stream().map(event -> event.withSeries(event.id(), null, null)).toList());
            if (whole) {
                series.delete(current);
            } else {
                series.save(current.endingBefore(day));
            }
            return List.of(events.save(changed.withSeries(occurrence.id(), null, null)));
        }

        if (!whole) {
            series.save(current.endingBefore(day));
        }
        EventSeries target = series.save(new EventSeries(whole ? current.id() : null, template, skipped, null));
        // Einzeln geänderte Termine wandern mit und zeigen die neue Regel
        events.saveAll(keptChanged.stream().map(event -> event.withSeries(event.id(), template.recurrence(),
                new SeriesRef(target.id(), event.series().date(), true))).toList());
        generate(target, target.firstDay());
        return events.findBySeries(target.id()).stream()
                .filter(event -> !event.start().toLocalDate().isBefore(template.start().toLocalDate()))
                .sorted(BY_START).toList();
    }

    // Nur diesen Termin löschen; der Tag kommt bei späteren Änderungen der Serie nicht wieder
    public void deleteOne(CalendarEvent occurrence) {
        EventSeries current = find(occurrence);
        events.delete(occurrence);
        if (current.template().isProposal()) {
            series.delete(current);
        } else {
            series.save(current.skipping(occurrence.series().date()));
        }
    }

    // Diesen und alle folgenden Termine löschen, auch einzeln geänderte. Ab dem ersten Termin ist das die ganze Serie.
    public void deleteFollowing(CalendarEvent occurrence) {
        EventSeries current = find(occurrence);
        LocalDate day = occurrence.series().date();
        List<CalendarEvent> all = events.findBySeries(current.id());
        events.deleteAll(following(occurrence));
        if (all.stream().noneMatch(event -> event.series().date().isBefore(day))) {
            series.delete(current);
        } else {
            series.save(current.endingBefore(day));
        }
    }

    // Vorschlag freigeben: Jetzt entstehen auch die übrigen Termine. Gibt alle Termine nach Beginn sortiert zurück.
    public List<CalendarEvent> approve(CalendarEvent proposal) {
        EventSeries current = find(proposal);
        EventSeries approved = series.save(current.withTemplate(current.template().withStatus(EventStatus.APPROVED)));
        events.saveAll(events.findBySeries(current.id()).stream()
                .map(event -> event.withStatus(EventStatus.APPROVED)).toList());
        generate(approved, approved.firstDay());
        return events.findBySeries(current.id()).stream().sorted(BY_START).toList();
    }

    public void reject(CalendarEvent proposal) {
        EventSeries current = find(proposal);
        events.deleteAll(events.findBySeries(current.id()));
        series.delete(current);
    }

    // Wird ein Mitglied gelöscht, legen seine Serien keine Termine mehr für es an (die Termine selbst trägt
    // FamilyMemberController aus). Serien nur für dieses Mitglied entfallen.
    public void forgetMember(String memberId) {
        for (EventSeries current : series.findByMember(memberId)) {
            if (current.template().isOnlyFor(memberId)) {
                series.delete(current);
            } else {
                series.save(current.withTemplate(current.template().withoutMember(memberId)));
            }
        }
    }

    // Täglich die Serien bis zum Vorlauf verlängern (Vorschläge erst nach der Freigabe)
    @Scheduled(cron = "${familyhub.calendar.series-extend-cron:0 20 3 * * *}")
    public void extendAll() {
        int created = 0;
        for (EventSeries current : series.findAll()) {
            if (!current.template().isProposal()) {
                LocalDate from = current.generatedUntil() == null ? current.firstDay() : current.generatedUntil().plusDays(1);
                created += generate(current, from).size();
            }
        }
        if (created > 0) {
            log.info("Terminserien verlängert: {} neue Termine", created);
        }
    }

    // Legt die fehlenden Termine ab from bis zum Vorlauf an, mindestens aber den ersten Termin der Serie
    private List<CalendarEvent> generate(EventSeries target, LocalDate from) {
        Recurrence rule = target.recurrence();
        LocalDate first = target.firstDay();
        LocalDate horizon = LocalDate.now(clock).plus(properties.seriesHorizon());
        LocalDate to = rule.firstDate(first).filter(day -> day.isAfter(horizon)).orElse(horizon);
        if (from.isAfter(to)) {
            return List.of();
        }
        Set<LocalDate> existing = events.findBySeries(target.id()).stream()
                .map(event -> event.series().date()).collect(Collectors.toSet());
        List<LocalDate> days = rule.dates(first, from, to, target.template().isProposal() ? 1 : Integer.MAX_VALUE);
        List<CalendarEvent> created = events.saveAll(days.stream()
                .filter(day -> !target.skippedDates().contains(day) && !existing.contains(day))
                .map(day -> target.template().occurrence(target.id(), day))
                .toList());
        series.save(target.withGeneratedUntil(to));
        return created.stream().sorted(BY_START).toList();
    }

    // Ergibt die Regel mindestens einen Termin, der nicht ausgelassen ist? Geprüft bis zum Vorlauf, mindestens bis
    // zum ersten Termin der Regel.
    private void requireSomeDate(CalendarEvent template, Set<LocalDate> skipped) {
        Recurrence rule = template.recurrence();
        LocalDate first = template.start().toLocalDate();
        LocalDate horizon = LocalDate.now(clock).plus(properties.seriesHorizon());
        boolean someDate = rule.firstDate(first)
                .map(day -> rule.dates(first, first, day.isAfter(horizon) ? day : horizon))
                .orElse(List.of())
                .stream().anyMatch(day -> !skipped.contains(day));
        if (!someDate) {
            throw ApiException.invalidField("recurrence", "Die Wiederholung ergibt keinen einzigen Termin");
        }
    }

    private EventSeries find(CalendarEvent occurrence) {
        return series.findById(occurrence.series().seriesId())
                .orElseThrow(() -> ApiException.conflict("Die Serie dieses Termins gibt es nicht mehr."));
    }
}
