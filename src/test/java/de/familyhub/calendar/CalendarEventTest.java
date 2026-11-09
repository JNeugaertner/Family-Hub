package de.familyhub.calendar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import tools.jackson.databind.json.JsonMapper;

class CalendarEventTest {

    private static final LocalDateTime START = LocalDateTime.of(2026, 9, 25, 10, 0);

    private static ValidatorFactory factory;
    private static Validator validator;
    private static final JsonMapper JSON = JsonMapper.builder().build();

    @BeforeAll
    static void setUp() {
        factory = Validation.buildDefaultValidatorFactory();
        validator = factory.getValidator();
    }

    @AfterAll
    static void tearDown() {
        factory.close();
    }

    private static CalendarEvent event(String title, LocalDateTime start, LocalDateTime end,
            String memberId, EventCategory category) {
        return new CalendarEvent(null, title, null, start, end, memberId, category, "Sports Center", null, null, null ,null, null);
    }

    private static CalendarEvent validEvent() {
        return event("Basketballspiel", START, START.plusHours(2), "4", EventCategory.SPORTS);
    }

    private Set<String> errors(CalendarEvent event) {
        return validator.validate(event).stream()
                .map(ConstraintViolation::getMessage)
                .collect(Collectors.toSet());
    }

    @Test
    void validEventHasNoErrors() {
        assertThat(errors(validEvent())).isEmpty();
    }

    @Test
    void blankTitleIsRejected() {
        assertThat(errors(event(" ", START, START.plusHours(1), "4", EventCategory.SPORTS)))
                .containsExactly("Titel darf nicht leer sein");
    }

    @Test
    void endBeforeStartIsRejected() {
        assertThat(errors(event("Training", START, START.minusMinutes(30), "4", EventCategory.SPORTS)))
                .containsExactly("Ende muss nach dem Beginn liegen");
    }

    @Test
    void endEqualToStartIsRejected() {
        assertThat(errors(event("Training", START, START, "4", EventCategory.SPORTS)))
                .containsExactly("Ende muss nach dem Beginn liegen");
    }

    @Test
    void eventOverMidnightIsValid() {
        LocalDateTime lateStart = LocalDateTime.of(2026, 9, 25, 22, 0);
        assertThat(errors(event("Nachtwanderung", lateStart, lateStart.plusHours(3), "3", EventCategory.FAMILY)))
                .isEmpty();
    }

    @Test
    void missingTimesAreReportedOnce() {
        assertThat(errors(event("Training", null, null, "4", EventCategory.SPORTS)))
                .containsExactlyInAnyOrder("Beginn ist Pflicht", "Ende ist Pflicht");
    }

    @Test
    void missingMemberAndCategoryAreRejected() {
        assertThat(errors(event("Training", START, START.plusHours(1), "", null)))
                .containsExactlyInAnyOrder(
                        "Termin muss mindestens einem Familienmitglied zugeordnet sein",
                        "Kategorie ist Pflicht");
    }

    @Test
    void jsonUsesUiCategoryNamesAndIsoTimes() {
        String json = JSON.writeValueAsString(validEvent());

        assertThat(json)
                .contains("\"category\":\"sports\"")
                .contains("\"start\":\"2026-09-25T10:00:00\"")
                .contains("\"end\":\"2026-09-25T12:00:00\"")
                .doesNotContain("endAfterStart");
    }

    @Test
    void jsonFromFrontendIsParsed() {
        String json = """
                {"title":"Zahnarzt Lucas","start":"2026-09-23T11:00","end":"2026-09-23T12:00",
                 "memberIds":["4"],"category":"appointment","location":"Bright Smile Dental"}
                """;

        CalendarEvent event = JSON.readValue(json, CalendarEvent.class);

        assertThat(event.category()).isEqualTo(EventCategory.APPOINTMENT);
        assertThat(event.start()).isEqualTo(LocalDateTime.of(2026, 9, 23, 11, 0));
        assertThat(errors(event)).isEmpty();
    }

    @Test
    void unknownCategoryIsRejectedWhenParsing() {
        String json = """
                {"title":"Test","start":"2026-09-23T11:00","end":"2026-09-23T12:00",
                 "memberIds":["4"],"category":"party"}
                """;

        assertThatThrownBy(() -> JSON.readValue(json, CalendarEvent.class))
                .hasMessageContaining("party");
    }

    // Fahrzeit und Serie stehen im selben Datensatz: Serientermine und Änderungen dürfen Ort und Fahrzeit nicht verlieren
    @Test
    void seriesOccurrencesAndCopiesKeepTravelData() {
        CalendarEvent template = new CalendarEvent(null, "Training", "17:35", START, START.plusHours(1), List.of("4", "5"),
                EventCategory.SPORTS, "Sporthalle", 48.1, 11.5, "cycling-regular", 600.0, null, false,
                EventStatus.APPROVED, "4", null,
                new Recurrence(Frequency.WEEKLY, 1, Set.of(DayOfWeek.FRIDAY), null, 3), null);

        CalendarEvent next = template.occurrence("s1", LocalDate.of(2026, 10, 2));
        assertThat(next.start()).isEqualTo(LocalDateTime.of(2026, 10, 2, 10, 0));
        assertThat(next.series().seriesId()).isEqualTo("s1");
        for (CalendarEvent e : List.of(next, template.withStatus(EventStatus.PROPOSED), template.withoutMember("5"),
                template.withSeries("x", template.recurrence(), next.series()))) {
            assertThat(e.location()).isEqualTo("Sporthalle");
            assertThat(e.lat()).isEqualTo(48.1);
            assertThat(e.lon()).isEqualTo(11.5);
            assertThat(e.transportMode()).isEqualTo("cycling-regular");
            assertThat(e.travelTime()).isEqualTo(600.0);
            assertThat(e.departure()).isEqualTo("17:35");
            assertThat(e.recurrence()).isEqualTo(template.recurrence());
        }
    }
}
