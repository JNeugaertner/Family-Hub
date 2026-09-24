package de.familyhub.security;

import java.util.Collection;
import java.util.List;

import org.springframework.security.core.CredentialsContainer;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import de.familyhub.family.FamilyMember;

// Angemeldete Person in der Sitzung. Rolle und Rechte werden bei jeder Anfrage frisch aus der
// Datenbank gelesen (CurrentMember), damit Änderungen durch einen Administrator sofort gelten.
public final class FamilyUserDetails implements UserDetails, CredentialsContainer {

    private final String memberId;
    private final String username;
    private String passwordHash;

    public FamilyUserDetails(FamilyMember member) {
        this.memberId = member.id();
        this.username = member.username();
        this.passwordHash = member.passwordHash();
    }

    public String memberId() {
        return memberId;
    }

    @Override
    public String getUsername() {
        return username;
    }

    @Override
    public String getPassword() {
        return passwordHash;
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return List.of(new SimpleGrantedAuthority("ROLE_MEMBER"));
    }

    @Override
    public void eraseCredentials() {
        passwordHash = null;
    }
}
