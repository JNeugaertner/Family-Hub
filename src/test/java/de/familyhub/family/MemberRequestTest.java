package de.familyhub.family;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import de.familyhub.permission.Role;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class MemberRequestTest {

    private static ValidatorFactory factory;
    private static Validator validator;

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private static MemberRequest request(String name, String color, String username, String password) {
        return new MemberRequest(name, color, username, password, Role.KIND, LocalDate.of(2018, 1, 30), false);
    }

    private Set<String> errors(MemberRequest request) {
        return validator.validate(request).stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toSet());
    }

    @Test
    void validRequestHasNoErrors() {
        assertThat(errors(request("Lily", "#EC4899", "lily", "geheim123"))).isEmpty();
    }

    @Test
    void passwordMayBeOmittedButNotTooShort() {
        assertThat(errors(request("Lily", "#EC4899", "lily", null))).isEmpty();
        assertThat(errors(request("Lily", "#EC4899", "lily", "kurz")))
                .containsExactly("Passwort muss mindestens 8 Zeichen lang sein");
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void blankNameIsRejected(String name) {
        assertThat(errors(request(name, "#EC4899", "lily", null))).containsExactly("Name darf nicht leer sein");
    }

    @ParameterizedTest
    @ValueSource(strings = {"blau", "2563EB", "#2563E", "#GGGGGG"})
    void invalidColorIsRejected(String color) {
        assertThat(errors(request("Lily", color, "lily", null)))
                .containsExactly("Farbe muss ein Hex-Wert wie #2563EB sein");
    }

    @ParameterizedTest
    @ValueSource(strings = {"Lily", "li", "lily meier", "lily!", "sehr-langer-benutzername-mit-mehr-als-30"})
    void invalidUsernameIsRejected(String username) {
        assertThat(errors(request("Lily", "#EC4899", username, null))).containsExactly(MemberRules.USERNAME_MESSAGE);
    }

    @Test
    void birthDateMustBeInThePast() {
        MemberRequest future = new MemberRequest("Lily", "#EC4899", "lily", null, Role.KIND,
                LocalDate.now().plusDays(1), false);
        assertThat(errors(future)).containsExactly("Geburtsdatum muss in der Vergangenheit liegen");
    }
}
