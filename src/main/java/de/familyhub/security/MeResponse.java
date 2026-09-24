package de.familyhub.security;

import java.time.LocalDate;
import java.util.List;

import de.familyhub.permission.Permission;
import de.familyhub.permission.Role;
import io.swagger.v3.oas.annotations.media.Schema;

public record MeResponse(
        String id,
        String name,
        String color,
        String username,
        Role role,
        Role effectiveRole,
        LocalDate birthDate,
        @Schema(description = "Alle geltenden Rechte (Rolle plus Einzelrechte); Grundlage für die Anzeige im Frontend")
        List<Permission> permissions) {
}
