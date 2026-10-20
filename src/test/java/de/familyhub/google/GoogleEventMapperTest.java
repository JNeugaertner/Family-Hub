package de.familyhub.google;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import de.familyhub.calendar.CalendarEvent;
import de.familyhub.calendar.EventCategory;
import de.familyhub.calendar.EventStatus;
import de.familyhub.calendar.ExternalRef;

class GoogleEventMapperTest {

    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-09-28T08:00:00Z"), ZoneId.of("Europe/Berlin"));
    private static final GoogleCalendarChoice FUSSBALL = new GoogleCalendarChoice("fussball@group.calendar.google.com",
            "Fußball", false, true, EventCategory.SPORTS);

    private final GoogleEventMapper mapper = new GoogleEventMapper(CLOCK);

    private static GoogleEvent.Time at(String dateTime) {
        return new GoogleEvent.Time(OffsetDateTime.parse(dateTime), null);
    }

    private static GoogleEvent.Time day(String date) {
        return new GoogleEvent.Time(null, LocalDate.parse(date));
    }

    private static GoogleEvent event(String summary, GoogleEvent.Time start, GoogleEvent.Time end) {
        return new GoogleEvent("e1", "confirmed", summary, null, null, null, start, end);
    }

    @Test
    void convertsTimesToFamilyHubTimeZoneAndKeepsOrigin() {
        CalendarEvent result = mapper.toEvent(new GoogleEvent("e1", "confirmed", "Training", "Halle 2",
                "Sporthalle", null, at("2026-09-29T15:00:00Z"), at("2026-09-29T16:30:00Z")), "lucas-id", FUSSBALL);

        assertThat(result.start()).isEqualTo(LocalDateTime.of(2026, 9, 29, 17, 0));
        assertThat(result.end()).isEqualTo(LocalDateTime.of(2026, 9, 29, 18, 30));
        assertThat(result.title()).isEqualTo("Training");
        assertThat(result.location()).isEqualTo("Sporthalle");
        assertThat(result.description()).isEqualTo("Halle 2");
        assertThat(result.memberIds()).containsExactly("lucas-id");
        assertThat(result.createdBy()).isEqualTo("lucas-id");
        assertThat(result.category()).isEqualTo(EventCategory.SPORTS);
        assertThat(result.status()).isEqualTo(EventStatus.APPROVED);
        assertThat(result.privateEvent()).isFalse();
        assertThat(result.external()).isEqualTo(new ExternalRef("google", "fussball@group.calendar.google.com", "e1"));
    }

    @Test
    void allDayEventsRunFromMidnightToMidnight() {
        CalendarEvent result = mapper.toEvent(event("Feiertag", day("2026-10-03"), day("2026-10-04")), "m", FUSSBALL);

        assertThat(result.start()).isEqualTo(LocalDateTime.of(2026, 10, 3, 0, 0));
        assertThat(result.end()).isEqualTo(LocalDateTime.of(2026, 10, 4, 0, 0));
    }

    @Test
    void privateAndConfidentialEventsBecomePrivate() {
        for (String visibility : new String[] {"private", "confidential"}) {
            GoogleEvent secret = new GoogleEvent("e1", "confirmed", "Arzt", null, null, visibility,
                    at("2026-09-29T08:00:00Z"), at("2026-09-29T09:00:00Z"));
            assertThat(mapper.toEvent(secret, "m", FUSSBALL).privateEvent()).isTrue();
        }
    }

    @Test
    void missingTitleLongTextsAndHtmlAreCleanedUp() {
        CalendarEvent untitled = mapper.toEvent(event("  ", at("2026-09-29T08:00:00Z"), at("2026-09-29T09:00:00Z")),
                "m", FUSSBALL);
        assertThat(untitled.title()).isEqualTo(GoogleEventMapper.NO_TITLE);

        CalendarEvent longOne = mapper.toEvent(new GoogleEvent("e1", "confirmed", "x".repeat(150),
                "<b>Treffpunkt</b><br>Eingang Nord", null, null, at("2026-09-29T08:00:00Z"),
                at("2026-09-29T09:00:00Z")), "m", FUSSBALL);
        assertThat(longOne.title()).hasSize(100).endsWith("…");
        assertThat(longOne.description()).isEqualTo("Treffpunkt\nEingang Nord");
    }

    @Test
    void eventsWithoutDurationGetHalfAnHour() {
        CalendarEvent result = mapper.toEvent(event("Erinnerung", at("2026-09-29T08:00:00Z"),
                at("2026-09-29T08:00:00Z")), "m", FUSSBALL);

        assertThat(result.end()).isEqualTo(result.start().plusMinutes(30));
    }

    @Test
    void cancelledEventsAreSkipped() {
        GoogleEvent cancelled = new GoogleEvent("e1", "cancelled", "Abgesagt", null, null, null,
                at("2026-09-29T08:00:00Z"), at("2026-09-29T09:00:00Z"));

        assertThat(mapper.toEvent(cancelled, "m", FUSSBALL)).isNull();
    }

    @Test
    void entriesFamilyHubWroteItselfAreNotImported() {
        GoogleEvent written = new GoogleEvent("e1", "confirmed", "Training", null, null, null,
                at("2026-09-29T08:00:00Z"), at("2026-09-29T09:00:00Z"),
                new GoogleEvent.ExtendedProperties(Map.of(GoogleEvent.FAMILYHUB_ID, "abc")));

        assertThat(mapper.toEvent(written, "m", FUSSBALL)).isNull();
    }

    private static CalendarEvent familyHubEvent(boolean privateEvent, String description) {
        return new CalendarEvent("abc", "Arzt", LocalDateTime.of(2026, 10, 1, 10, 0),
                LocalDateTime.of(2026, 10, 1, 11, 0), List.of("m"), EventCategory.APPOINTMENT, "Praxis", description,
                privateEvent, EventStatus.APPROVED, "m", null);
    }

    @Test
    void familyHubEventsBecomeGoogleEntriesInFamilyHubTimeZone() {
        GoogleEventInput input = mapper.toGoogle(familyHubEvent(true, " "));

        assertThat(input.summary()).isEqualTo("Arzt");
        assertThat(input.start()).isEqualTo(new GoogleEventInput.Time("2026-10-01T10:00:00+02:00", "Europe/Berlin"));
        assertThat(input.end()).isEqualTo(new GoogleEventInput.Time("2026-10-01T11:00:00+02:00", "Europe/Berlin"));
        assertThat(input.location()).isEqualTo("Praxis");
        assertThat(input.description()).isNull();
        assertThat(input.visibility()).isEqualTo("private");
        assertThat(input.extendedProperties().privateProperties()).containsEntry(GoogleEvent.FAMILYHUB_ID, "abc");
        assertThat(mapper.toGoogle(familyHubEvent(false, null)).visibility()).isEqualTo("default");
    }

    @Test
    void entryIsUpToDateOnlyWhileItMatchesTheFamilyHubEvent() {
        CalendarEvent event = familyHubEvent(false, "Impfpass mitbringen");
        GoogleEvent same = new GoogleEvent("g1", "confirmed", "Arzt", "Impfpass mitbringen", "Praxis", null,
                at("2026-10-01T08:00:00Z"), at("2026-10-01T09:00:00Z"));

        assertThat(mapper.isUpToDate(same, event)).isTrue();
        assertThat(mapper.isUpToDate(new GoogleEvent("g1", "confirmed", "Arzt (in Google geändert)",
                "Impfpass mitbringen", "Praxis", null, same.start(), same.end()), event)).isFalse();
        assertThat(mapper.isUpToDate(new GoogleEvent("g1", "confirmed", "Arzt", "Impfpass mitbringen", "Praxis", null,
                at("2026-10-01T09:00:00Z"), at("2026-10-01T10:00:00Z")), event)).isFalse();
        assertThat(mapper.isUpToDate(new GoogleEvent("g1", "confirmed", "Arzt", "Impfpass mitbringen", "Praxis",
                "private", same.start(), same.end()), event)).isFalse();
        assertThat(mapper.isUpToDate(new GoogleEvent("g1", "confirmed", "Arzt", "Impfpass mitbringen", "Praxis", null,
                day("2026-10-01"), day("2026-10-02")), event)).isFalse();
    }
}
