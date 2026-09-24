package de.familyhub.testsupport;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;

import java.time.LocalDate;
import java.util.Locale;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import de.familyhub.family.FamilyMember;
import de.familyhub.permission.Role;
import de.familyhub.security.FamilyUserDetails;

public final class TestUsers {

    public static final String PASSWORD = "passwort123";
    // {noop}: unverschlüsselt, damit Tests nicht auf BCrypt warten
    private static final String PASSWORD_HASH = "{noop}" + PASSWORD;

    private TestUsers() {
    }

    public static FamilyMember member(String name, Role role) {
        return member(name, role, null);
    }

    public static FamilyMember member(String name, Role role, LocalDate birthDate) {
        return new FamilyMember(null, name, "#2563EB", name.toLowerCase(Locale.ROOT), PASSWORD_HASH, role,
                birthDate, false);
    }

    // Anfrage als angemeldete Person, inklusive gültigem CSRF-Token. Das Mitglied muss gespeichert sein.
    public static RequestPostProcessor as(FamilyMember member) {
        FamilyUserDetails principal = new FamilyUserDetails(member);
        RequestPostProcessor login = authentication(
                UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities()));
        RequestPostProcessor csrfToken = csrf();
        return request -> csrfToken.postProcessRequest(login.postProcessRequest(request));
    }
}
