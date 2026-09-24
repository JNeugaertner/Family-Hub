package de.familyhub.family;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@Document("members")
public record FamilyMember(
        @Id @Schema(accessMode = Schema.AccessMode.READ_ONLY) String id,

        @NotBlank(message = "Name darf nicht leer sein")
        @Size(max = 50, message = "Name darf höchstens 50 Zeichen lang sein")
        String name,

        @NotNull(message = "Farbe ist Pflicht")
        @Pattern(regexp = "^#[0-9A-Fa-f]{6}$", message = "Farbe muss ein Hex-Wert wie #2563EB sein")
        String color) {
}
