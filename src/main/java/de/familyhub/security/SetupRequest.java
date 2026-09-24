package de.familyhub.security;

import de.familyhub.family.MemberRules;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record SetupRequest(
        @NotBlank(message = "Name darf nicht leer sein")
        @Size(max = 50, message = "Name darf höchstens 50 Zeichen lang sein")
        String name,

        @NotNull(message = "Farbe ist Pflicht")
        @Pattern(regexp = MemberRules.COLOR_PATTERN, message = "Farbe muss ein Hex-Wert wie #2563EB sein")
        String color,

        @NotBlank(message = "Benutzername ist Pflicht")
        @Pattern(regexp = MemberRules.USERNAME_PATTERN, message = MemberRules.USERNAME_MESSAGE)
        String username,

        @NotNull(message = "Passwort ist Pflicht")
        @Size(min = MemberRules.PASSWORD_MIN_LENGTH, max = 100, message = MemberRules.PASSWORD_MESSAGE)
        String password) {
}
