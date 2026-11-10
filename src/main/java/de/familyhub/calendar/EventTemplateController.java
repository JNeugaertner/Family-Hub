package de.familyhub.calendar;

import java.net.URI;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import de.familyhub.family.FamilyMember;
import de.familyhub.security.CurrentMember;
import de.familyhub.web.ApiException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

// Vorlagen für Termine (siehe EventTemplate). Rechte: CalendarAccess.
@RestController
@RequestMapping("/api/event-templates")
@Tag(name = "Termine")
public class EventTemplateController {

    private final EventTemplateRepository templates;
    private final CurrentMember currentMember;
    private final CalendarAccess access;
    private final Clock clock;

    public EventTemplateController(EventTemplateRepository templates, CurrentMember currentMember,
            CalendarAccess access, Clock clock) {
        this.templates = templates;
        this.currentMember = currentMember;
        this.access = access;
        this.clock = clock;
    }

    @GetMapping
    @Operation(summary = "Vorlagen für Termine",
            description = "Alphabetisch nach Name. Private Vorlagen nur für die Person, die sie angelegt hat.")
    public List<EventTemplate> list() {
        FamilyMember viewer = currentMember.get();
        access.requireTemplates(viewer);
        return templates.findAll().stream()
                .filter(t -> access.canSeeTemplate(viewer, t))
                .sorted(Comparator.comparing(EventTemplate::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @PostMapping
    @Operation(summary = "Vorlage anlegen", description = "Für alle, die Termine anlegen oder vorschlagen dürfen.")
    public ResponseEntity<EventTemplate> create(@Valid @RequestBody EventTemplate template) {
        FamilyMember viewer = currentMember.get();
        access.requireTemplates(viewer);
        EventTemplate saved = templates.save(template.createdBy(viewer.id(), LocalDateTime.now(clock)));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(saved.id()).toUri();
        return ResponseEntity.created(location).body(saved);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Vorlage ändern", description = "Wer sie angelegt hat, oder ein Administrator. "
            + "Termine, die schon aus der Vorlage entstanden sind, ändern sich nicht.")
    public EventTemplate update(@PathVariable String id, @Valid @RequestBody EventTemplate template) {
        FamilyMember viewer = currentMember.get();
        EventTemplate existing = find(viewer, id);
        access.requireTemplateChange(viewer, existing);
        return templates.save(existing.withContent(template));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Vorlage löschen", description = "Termine aus der Vorlage bleiben bestehen.")
    public void delete(@PathVariable String id) {
        FamilyMember viewer = currentMember.get();
        access.requireTemplateChange(viewer, find(viewer, id));
        templates.deleteById(id);
    }

    // Unsichtbare Vorlagen (fremde private) verhalten sich wie nicht vorhandene
    private EventTemplate find(FamilyMember viewer, String id) {
        return templates.findById(id).filter(t -> access.canSeeTemplate(viewer, t))
                .orElseThrow(() -> ApiException.notFound("Vorlage " + id + " existiert nicht."));
    }
}
