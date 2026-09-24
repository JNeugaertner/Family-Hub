package de.familyhub.calendar;

import java.net.URI;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import de.familyhub.family.FamilyMember;
import de.familyhub.family.FamilyMemberRepository;
import de.familyhub.security.CurrentMember;
import de.familyhub.web.ApiException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/events")
@Tag(name = "Termine")
public class CalendarEventController {

    private final CalendarEventRepository events;
    private final FamilyMemberRepository members;
    private final CurrentMember currentMember;
    private final CalendarAccess access;

    public CalendarEventController(CalendarEventRepository events, FamilyMemberRepository members,
            CurrentMember currentMember, CalendarAccess access) {
        this.events = events;
        this.members = members;
        this.currentMember = currentMember;
        this.access = access;
    }

    @GetMapping
    @Operation(summary = "Termine abfragen",
            description = "Ohne Parameter alle Termine. Mit from und to alle Termine, die den Zeitraum berühren "
                    + "(auch über Mitternacht). Immer nach Beginn sortiert. Enthält nur Termine, die die "
                    + "angemeldete Person sehen darf.")
    public List<CalendarEvent> list(
            @Parameter(description = "Beginn des Zeitraums, z. B. 2026-09-21T00:00")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @Parameter(description = "Ende des Zeitraums (exklusiv), z. B. 2026-09-28T00:00")
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @Parameter(description = "Nur Termine dieses Familienmitglieds")
            @RequestParam(required = false) String memberId) {

        if ((from == null) != (to == null)) {
            throw ApiException.invalidField(from == null ? "from" : "to", "from und to müssen zusammen angegeben werden");
        }
        if (from != null && !to.isAfter(from)) {
            throw ApiException.invalidField("to", "to muss nach from liegen");
        }
        FamilyMember viewer = currentMember.get();
        List<CalendarEvent> result;
        if (from == null) {
            result = memberId == null
                    ? events.findAll(Sort.by("start"))
                    : events.findByMemberIdOrderByStartAsc(memberId);
        } else {
            result = memberId == null
                    ? events.findOverlapping(from, to)
                    : events.findOverlappingForMember(memberId, from, to);
        }
        return result.stream().filter(event -> access.canSee(viewer, event)).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Ein Termin")
    public CalendarEvent get(@PathVariable String id) {
        return findVisible(id, currentMember.get());
    }

    @PostMapping
    @Operation(summary = "Termin anlegen", description = "Eine mitgeschickte id wird ignoriert.")
    public ResponseEntity<CalendarEvent> create(@Valid @RequestBody CalendarEvent event) {
        FamilyMember viewer = currentMember.get();
        access.requireCreate(viewer, event);
        requireMember(event.memberId());
        CalendarEvent saved = events.save(withId(null, event));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(saved.id()).toUri();
        return ResponseEntity.created(location).body(saved);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Termin ändern")
    public CalendarEvent update(@PathVariable String id, @Valid @RequestBody CalendarEvent event) {
        FamilyMember viewer = currentMember.get();
        CalendarEvent existing = findVisible(id, viewer);
        access.requireUpdate(viewer, existing, event);
        requireMember(event.memberId());
        return events.save(withId(id, event));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Termin löschen")
    public void delete(@PathVariable String id) {
        FamilyMember viewer = currentMember.get();
        CalendarEvent existing = findVisible(id, viewer);
        access.requireDelete(viewer, existing);
        events.deleteById(id);
    }

    // Termine, die jemand nicht sehen darf, gelten für ihn als nicht vorhanden.
    private CalendarEvent findVisible(String id, FamilyMember viewer) {
        return events.findById(id)
                .filter(event -> access.canSee(viewer, event))
                .orElseThrow(() -> ApiException.notFound("Termin " + id + " existiert nicht."));
    }

    private void requireMember(String memberId) {
        if (!members.existsById(memberId)) {
            throw ApiException.invalidField("memberId", "Familienmitglied existiert nicht");
        }
    }

    private static CalendarEvent withId(String id, CalendarEvent e) {
        return new CalendarEvent(id, e.title(), e.start(), e.end(), e.memberId(), e.category(), e.location(), e.description());
    }
}
