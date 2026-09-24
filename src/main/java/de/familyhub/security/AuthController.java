package de.familyhub.security;

import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import de.familyhub.family.FamilyMember;
import de.familyhub.family.FamilyMemberRepository;
import de.familyhub.permission.Role;
import de.familyhub.web.ApiException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

// Login (POST /api/auth/login, Formularfelder username und password) und Logout (POST /api/auth/logout)
// stellt Spring Security bereit, siehe SecurityConfig.
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Anmeldung")
public class AuthController {

    private final FamilyMemberRepository members;
    private final PasswordEncoder passwordEncoder;
    private final CurrentMember currentMember;
    private final MeResponses meResponses;

    public AuthController(FamilyMemberRepository members, PasswordEncoder passwordEncoder,
            CurrentMember currentMember, MeResponses meResponses) {
        this.members = members;
        this.passwordEncoder = passwordEncoder;
        this.currentMember = currentMember;
        this.meResponses = meResponses;
    }

    @GetMapping("/status")
    @Operation(summary = "Prüfen, ob die Ersteinrichtung noch aussteht", description = "Ohne Anmeldung abrufbar.")
    public AuthStatus status() {
        return new AuthStatus(members.count() == 0);
    }

    @PostMapping("/setup")
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Ersteinrichtung: ersten Administrator anlegen",
            description = "Nur möglich, solange noch kein Familienmitglied existiert. Danach normal anmelden.")
    public MeResponse setup(@Valid @RequestBody SetupRequest request) {
        if (members.count() > 0) {
            throw ApiException.conflict("Die Ersteinrichtung ist bereits abgeschlossen. Bitte anmelden.");
        }
        FamilyMember admin = members.save(new FamilyMember(null, request.name(), request.color(), request.username(),
                passwordEncoder.encode(request.password()), Role.ADMINISTRATOR, null, false));
        return meResponses.of(admin);
    }

    @GetMapping("/me")
    @Operation(summary = "Angemeldete Person")
    public MeResponse me() {
        return meResponses.of(currentMember.get());
    }

    @PutMapping("/password")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Eigenes Passwort ändern")
    public void changePassword(@Valid @RequestBody PasswordChangeRequest request) {
        FamilyMember me = currentMember.get();
        if (!passwordEncoder.matches(request.currentPassword(), me.passwordHash())) {
            throw ApiException.invalidField("currentPassword", "Das aktuelle Passwort ist falsch");
        }
        members.save(me.withPasswordHash(passwordEncoder.encode(request.newPassword())));
    }

    public record AuthStatus(boolean setupRequired) {
    }
}
