package de.familyhub.family;

import java.time.LocalDate;
import java.util.Set;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import de.familyhub.permission.Permission;
import de.familyhub.permission.Role;

// Gespeichertes Familienmitglied inklusive Zugangsdaten. Wird nie direkt als JSON ausgegeben,
// sondern immer über MemberResponse, damit der Passwort-Hash das Backend nicht verlässt.
@Document("members")
public record FamilyMember(
        @Id String id,
        String name,
        String color,
        @Indexed(unique = true, sparse = true) String username,
        String passwordHash,
        Role role,
        LocalDate birthDate,
        boolean roleFixed,
        // Einzelrechte, die ein Administrator zusätzlich vergibt bzw. entzieht (Rollenkonzept)
        Set<Permission> extraPermissions,
        Set<Permission> revokedPermissions) {

    @PersistenceCreator
    public FamilyMember {
        extraPermissions = extraPermissions == null ? Set.of() : Set.copyOf(extraPermissions);
        revokedPermissions = revokedPermissions == null ? Set.of() : Set.copyOf(revokedPermissions);
    }

    public FamilyMember(String id, String name, String color, String username, String passwordHash, Role role,
            LocalDate birthDate, boolean roleFixed) {
        this(id, name, color, username, passwordHash, role, birthDate, roleFixed, Set.of(), Set.of());
    }

    public FamilyMember withPasswordHash(String newPasswordHash) {
        return new FamilyMember(id, name, color, username, newPasswordHash, role, birthDate, roleFixed,
                extraPermissions, revokedPermissions);
    }
}
