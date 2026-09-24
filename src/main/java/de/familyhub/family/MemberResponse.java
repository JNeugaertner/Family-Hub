package de.familyhub.family;

import java.time.LocalDate;
import java.util.List;

import de.familyhub.permission.Permission;
import de.familyhub.permission.Role;
import io.swagger.v3.oas.annotations.media.Schema;

public record MemberResponse(
        String id,
        String name,
        String color,
        @Schema(description = "Nur für Administratoren und die Person selbst sichtbar")
        String username,
        @Schema(description = "Vom Administrator gesetzte Rolle")
        Role role,
        @Schema(description = "Tatsächlich geltende Rolle, z. B. Jugendlicher ab 13 statt Kind")
        Role effectiveRole,
        @Schema(description = "Nur für Administratoren und die Person selbst sichtbar")
        LocalDate birthDate,
        boolean roleFixed,
        @Schema(description = "Zusätzlich vergebene Einzelrechte, nur für Administratoren sichtbar")
        List<Permission> extraPermissions,
        @Schema(description = "Entzogene Einzelrechte, nur für Administratoren sichtbar")
        List<Permission> revokedPermissions) {
}
