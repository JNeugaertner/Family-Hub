package de.familyhub.family;

import java.time.LocalDate;
import java.util.Set;

import de.familyhub.permission.Permission;
import de.familyhub.permission.Role;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record MemberRequest(
        @NotBlank(message = "Name darf nicht leer sein")
        @Size(max = 50, message = "Name darf höchstens 50 Zeichen lang sein")
        String name,

        @NotNull(message = "Farbe ist Pflicht")
        @Pattern(regexp = MemberRules.COLOR_PATTERN, message = "Farbe muss ein Hex-Wert wie #2563EB sein")
        String color,

        @NotBlank(message = "Benutzername ist Pflicht")
        @Pattern(regexp = MemberRules.USERNAME_PATTERN, message = MemberRules.USERNAME_MESSAGE)
        String username,

        @Schema(description = "Pflicht beim Anlegen; beim Ändern leer lassen, um das Passwort zu behalten")
        @Size(min = MemberRules.PASSWORD_MIN_LENGTH, max = 100, message = MemberRules.PASSWORD_MESSAGE)
        String password,

        @NotNull(message = "Rolle ist Pflicht")
        Role role,

        @Past(message = "Geburtsdatum muss in der Vergangenheit liegen")
        LocalDate birthDate,

        @Schema(description = "true: kein automatischer Wechsel von Kind zu Jugendlicher ab 13. Standard: false")
        Boolean roleFixed,

        @Schema(description = "Zusätzliche Einzelrechte. Weglassen, um sie beim Ändern unverändert zu lassen")
        Set<@Valid Permission> extraPermissions,

        @Schema(description = "Entzogene Einzelrechte. Weglassen, um sie beim Ändern unverändert zu lassen")
        Set<@Valid Permission> revokedPermissions) {

    public boolean isRoleFixed() {
        return Boolean.TRUE.equals(roleFixed);
    }
}
