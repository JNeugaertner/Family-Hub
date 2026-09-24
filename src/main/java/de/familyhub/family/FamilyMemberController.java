package de.familyhub.family;

import java.net.URI;
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

import de.familyhub.calendar.CalendarEventRepository;
import de.familyhub.web.ApiException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/members")
@Tag(name = "Familienmitglieder")
public class FamilyMemberController {

    private final FamilyMemberRepository members;
    private final CalendarEventRepository events;

    public FamilyMemberController(FamilyMemberRepository members, CalendarEventRepository events) {
        this.members = members;
        this.events = events;
    }

    @GetMapping
    @Operation(summary = "Alle Familienmitglieder")
    public List<FamilyMember> list() {
        return members.findAll();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Ein Familienmitglied")
    public FamilyMember get(@PathVariable String id) {
        return find(id);
    }

    @PostMapping
    @Operation(summary = "Familienmitglied anlegen", description = "Eine mitgeschickte id wird ignoriert.")
    public ResponseEntity<FamilyMember> create(@Valid @RequestBody FamilyMember member) {
        FamilyMember saved = members.save(new FamilyMember(null, member.name(), member.color()));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(saved.id()).toUri();
        return ResponseEntity.created(location).body(saved);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Familienmitglied ändern")
    public FamilyMember update(@PathVariable String id, @Valid @RequestBody FamilyMember member) {
        find(id);
        return members.save(new FamilyMember(id, member.name(), member.color()));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Familienmitglied löschen", description = "Nur möglich, wenn dem Mitglied keine Termine mehr zugeordnet sind.")
    public void delete(@PathVariable String id) {
        find(id);
        long eventCount = events.countByMemberId(id);
        if (eventCount > 0) {
            String termine = eventCount == 1 ? "einen Termin" : eventCount + " Termine";
            throw ApiException.conflict("Das Familienmitglied hat noch " + termine
                    + ". Bitte zuerst die Termine löschen oder einem anderen Mitglied zuordnen.");
        }
        members.deleteById(id);
    }

    private FamilyMember find(String id) {
        return members.findById(id)
                .orElseThrow(() -> ApiException.notFound("Familienmitglied " + id + " existiert nicht."));
    }
}
