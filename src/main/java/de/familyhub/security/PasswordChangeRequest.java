package de.familyhub.security;

import de.familyhub.family.MemberRules;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record PasswordChangeRequest(
        @NotBlank(message = "Aktuelles Passwort ist Pflicht")
        String currentPassword,

        @NotNull(message = "Neues Passwort ist Pflicht")
        @Size(min = MemberRules.PASSWORD_MIN_LENGTH, max = 100, message = MemberRules.PASSWORD_MESSAGE)
        String newPassword) {
}
