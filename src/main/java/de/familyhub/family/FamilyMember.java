package de.familyhub.family;

import java.time.LocalDate;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

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
        boolean roleFixed) {
}
