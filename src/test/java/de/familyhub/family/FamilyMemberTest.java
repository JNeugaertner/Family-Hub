package de.familyhub.family;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;

class FamilyMemberTest {

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

    private Set<String> errors(FamilyMember member) {
        return validator.validate(member).stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toSet());
    }

    @Test
    void validMemberHasNoErrors() {
        assertThat(errors(new FamilyMember(null, "Sarah", "#2563EB"))).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "   "})
    void blankNameIsRejected(String name) {
        assertThat(errors(new FamilyMember(null, name, "#2563EB")))
                .containsExactly("Name darf nicht leer sein");
    }

    @Test
    void tooLongNameIsRejected() {
        assertThat(errors(new FamilyMember(null, "x".repeat(51), "#2563EB")))
                .containsExactly("Name darf höchstens 50 Zeichen lang sein");
    }

    @ParameterizedTest
    @ValueSource(strings = {"blau", "2563EB", "#2563E", "#2563EBFF", "#GGGGGG"})
    void invalidColorIsRejected(String color) {
        assertThat(errors(new FamilyMember(null, "Sarah", color)))
                .containsExactly("Farbe muss ein Hex-Wert wie #2563EB sein");
    }

    @Test
    void missingColorIsRejected() {
        assertThat(errors(new FamilyMember(null, "Sarah", null)))
                .containsExactly("Farbe ist Pflicht");
    }
}
