package de.familyhub.family;

import java.net.URI;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
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
import de.familyhub.permission.Role;
import de.familyhub.security.CurrentMember;
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
    private final CurrentMember currentMember;
    private final MemberResponses responses;
    private final FamilyRules rules;
    private final PasswordEncoder passwordEncoder;

    public FamilyMemberController(FamilyMemberRepository members, CalendarEventRepository events,
            CurrentMember currentMember, MemberResponses responses, FamilyRules rules,
            PasswordEncoder passwordEncoder) {
        this.members = members;
        this.events = events;
        this.currentMember = currentMember;
        this.responses = responses;
        this.rules = rules;
        this.passwordEncoder = passwordEncoder;
    }

    @GetMapping
    @Operation(summary = "Alle Familienmitglieder")
    public List<MemberResponse> list() {
        FamilyMember viewer = currentMember.get();
        return members.findAll().stream().map(m -> responses.of(m, viewer)).toList();
    }

    @GetMapping("/{id}")
    @Operation(summary = "Ein Familienmitglied")
    public MemberResponse get(@PathVariable String id) {
        return responses.of(find(id), currentMember.get());
    }

    @PostMapping
    @Operation(summary = "Familienmitglied anlegen", description = "Nur für Administratoren. Passwort ist Pflicht.")
    public ResponseEntity<MemberResponse> create(@Valid @RequestBody MemberRequest request) {
        FamilyMember admin = requireAdministrator();
        checkRoleAssignable(request.role());
        if (request.password() == null) {
            throw ApiException.invalidField("password", "Passwort ist Pflicht");
        }
        requireFreeUsername(request.username(), null);
        rules.checkLimits(request.role(), null);

        FamilyMember saved = members.save(new FamilyMember(null, request.name(), request.color(), request.username(),
                passwordEncoder.encode(request.password()), request.role(), request.birthDate(), request.isRoleFixed()));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(saved.id()).toUri();
        return ResponseEntity.created(location).body(responses.of(saved, admin));
    }

    @PutMapping("/{id}")
    @Operation(summary = "Familienmitglied ändern",
            description = "Nur für Administratoren. Ohne Passwort bleibt das bisherige Passwort erhalten.")
    public MemberResponse update(@PathVariable String id, @Valid @RequestBody MemberRequest request) {
        FamilyMember admin = requireAdministrator();
        FamilyMember existing = find(id);
        checkRoleAssignable(request.role());
        requireFreeUsername(request.username(), id);
        rules.checkLimits(request.role(), id);
        rules.checkAdministratorRemains(existing, request.role());

        String passwordHash = request.password() == null
                ? existing.passwordHash()
                : passwordEncoder.encode(request.password());
        FamilyMember saved = members.save(new FamilyMember(id, request.name(), request.color(), request.username(),
                passwordHash, request.role(), request.birthDate(), request.isRoleFixed()));
        return responses.of(saved, admin);
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Familienmitglied löschen",
            description = "Nur für Administratoren und nur, wenn dem Mitglied keine Termine mehr zugeordnet sind.")
    public void delete(@PathVariable String id) {
        requireAdministrator();
        FamilyMember member = find(id);
        rules.checkAdministratorRemains(member, null);
        long eventCount = events.countByMemberId(id);
        if (eventCount > 0) {
            String termine = eventCount == 1 ? "einen Termin" : eventCount + " Termine";
            throw ApiException.conflict("Das Familienmitglied hat noch " + termine
                    + ". Bitte zuerst die Termine löschen oder einem anderen Mitglied zuordnen.");
        }
        members.deleteById(id);
    }

    private FamilyMember requireAdministrator() {
        FamilyMember current = currentMember.get();
        if (!responses.isAdmin(current)) {
            throw ApiException.forbidden("Nur Administratoren dürfen Familienmitglieder verwalten.");
        }
        return current;
    }

    private static void checkRoleAssignable(Role role) {
        if (role == Role.KI_AGENT) {
            throw ApiException.invalidField("role", "Die Rolle KI-Agent kann keinem Familienmitglied zugewiesen werden");
        }
    }

    private void requireFreeUsername(String username, String ownId) {
        members.findByUsername(username)
                .filter(other -> !other.id().equals(ownId))
                .ifPresent(other -> {
                    throw ApiException.invalidField("username", "Benutzername ist bereits vergeben");
                });
    }

    private FamilyMember find(String id) {
        return members.findById(id)
                .orElseThrow(() -> ApiException.notFound("Familienmitglied " + id + " existiert nicht."));
    }
}
