package de.familyhub.google;

import static de.familyhub.testsupport.TestUsers.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Set;

import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import de.familyhub.calendar.CalendarEvent;
import de.familyhub.calendar.CalendarEventRepository;
import de.familyhub.calendar.EventCategory;
import de.familyhub.family.FamilyMember;
import de.familyhub.family.FamilyMemberRepository;
import de.familyhub.permission.Role;
import de.familyhub.settings.FamilySettings;
import de.familyhub.settings.FamilySettingsRepository;
import de.familyhub.testsupport.TestUsers;

// Abgleich mit Google (simuliert): Termine kommen an, ändern sich, verschwinden; importierte Termine sind
// schreibgeschützt und folgen den normalen Sichtbarkeitsregeln.
@SpringBootTest
@AutoConfigureMockMvc
class GoogleCalendarSyncTest {

    private static final String PRIMARY = "emma@example.com";
    private static final String FUSSBALL = "fussball@group.calendar.google.com";
    private static final String SCHULE = "schule@group.calendar.google.com";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private FamilyMemberRepository members;

    @Autowired
    private CalendarEventRepository events;

    @Autowired
    private GoogleConnectionRepository connections;

    @Autowired
    private FamilySettingsRepository settings;

    @Autowired
    private TokenCipher cipher;

    @Autowired
    private GoogleCalendarSync sync;

    @MockitoBean
    private GoogleApi google;

    private FamilyMember sarah;
    private FamilyMember emma;
    private FamilyMember lucas;
    private FamilyMember oma;
    private GoogleConnection connection;

    @BeforeEach
    void setUp() {
        events.deleteAll();
        connections.deleteAll();
        members.deleteAll();
        settings.deleteAll();
        sarah = members.save(TestUsers.member("Sarah", Role.ADMINISTRATOR));
        emma = members.save(TestUsers.member("Emma", Role.JUGENDLICHER));
        lucas = members.save(TestUsers.member("Lucas", Role.KIND));
        oma = members.save(TestUsers.member("Oma", Role.GAST));
        connection = connections.save(new GoogleConnection(null, emma.id(), PRIMARY, cipher.encrypt("refresh-1"),
                Instant.now(), List.of(
                        new GoogleCalendarChoice(PRIMARY, "Emma", true, true, EventCategory.APPOINTMENT),
                        new GoogleCalendarChoice(FUSSBALL, "Fußball", false, true, EventCategory.SPORTS),
                        new GoogleCalendarChoice(SCHULE, "Schule", false, false, EventCategory.SCHOOL)),
                null, null, false));
        when(google.refreshAccessToken("refresh-1")).thenReturn("access-1");
    }

    private static GoogleEvent googleEvent(String id, String title, String visibility, int day) {
        return new GoogleEvent(id, "confirmed", title, null, null, visibility,
                new GoogleEvent.Time(OffsetDateTime.parse("2026-10-%02dT10:00:00+02:00".formatted(day)), null),
                new GoogleEvent.Time(OffsetDateTime.parse("2026-10-%02dT11:00:00+02:00".formatted(day)), null));
    }

    private void googleHas(String calendarId, GoogleEvent... googleEvents) {
        when(google.listEvents(eq("access-1"), eq(calendarId), any(), any())).thenReturn(List.of(googleEvents));
    }

    private List<CalendarEvent> imported() {
        return events.findImportedByMember(emma.id());
    }

    private CalendarEvent importedWithTitle(String title) {
        return imported().stream().filter(e -> e.title().equals(title)).findFirst().orElseThrow();
    }

    @Test
    void importsActiveCalendarsInTheirCategories() {
        googleHas(PRIMARY, googleEvent("p1", "Zahnarzt", null, 1));
        googleHas(FUSSBALL, googleEvent("f1", "Training", null, 2));
        googleHas(SCHULE, googleEvent("s1", "Klassenarbeit", null, 3));

        GoogleConnection result = sync.sync(connection);

        assertThat(imported()).extracting(CalendarEvent::title).containsExactlyInAnyOrder("Zahnarzt", "Training");
        assertThat(importedWithTitle("Zahnarzt").category()).isEqualTo(EventCategory.APPOINTMENT);
        assertThat(importedWithTitle("Training").category()).isEqualTo(EventCategory.SPORTS);
        assertThat(importedWithTitle("Training").start()).isEqualTo(LocalDateTime.of(2026, 10, 2, 10, 0));
        assertThat(result.lastSyncAt()).isNotNull();
        assertThat(result.lastSyncError()).isNull();
    }

    @Test
    void requestsOneYearBackAndAhead() {
        sync.sync(connection);

        ArgumentCaptor<Instant> from = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> to = ArgumentCaptor.forClass(Instant.class);
        verify(google).listEvents(eq("access-1"), eq(PRIMARY), from.capture(), to.capture());
        assertThat(Duration.between(from.getValue(), Instant.now()).toDays()).isBetween(364L, 366L);
        assertThat(Duration.between(Instant.now(), to.getValue()).toDays()).isBetween(364L, 366L);
    }

    @Test
    void secondSyncUpdatesChangedAndRemovesDeletedEventsButKeepsOwnEvents() {
        CalendarEvent own = events.save(new CalendarEvent(null, "Eigener Termin", LocalDateTime.of(2026, 10, 1, 8, 0),
                LocalDateTime.of(2026, 10, 1, 9, 0), emma.id(), EventCategory.FAMILY, null, null));
        googleHas(PRIMARY, googleEvent("p1", "Zahnarzt", null, 1), googleEvent("p2", "Friseur", null, 2));
        googleHas(FUSSBALL, googleEvent("f1", "Training", null, 3));
        sync.sync(connection);
        String zahnarztId = importedWithTitle("Zahnarzt").id();
        String trainingId = importedWithTitle("Training").id();

        googleHas(PRIMARY, googleEvent("p1", "Zahnarzt (verschoben)", null, 4));
        sync.sync(connection);

        assertThat(imported()).extracting(CalendarEvent::title)
                .containsExactlyInAnyOrder("Zahnarzt (verschoben)", "Training");
        assertThat(importedWithTitle("Zahnarzt (verschoben)").id()).isEqualTo(zahnarztId);
        assertThat(importedWithTitle("Training").id()).isEqualTo(trainingId);
        assertThat(events.findById(own.id())).isPresent();
    }

    @Test
    void expiredAccessKeepsEventsAndAsksToReconnect() {
        googleHas(PRIMARY, googleEvent("p1", "Zahnarzt", null, 1));
        sync.sync(connection);
        when(google.refreshAccessToken("refresh-1")).thenThrow(new GoogleException("abgelaufen", true));

        GoogleConnection result = sync.sync(connections.findByMemberId(emma.id()).orElseThrow());

        assertThat(result.needsReconnect()).isTrue();
        assertThat(result.lastSyncError()).isEqualTo("abgelaufen");
        assertThat(imported()).hasSize(1);
    }

    @Test
    void syncNowAndCalendarSelectionViaApi() throws Exception {
        googleHas(PRIMARY, googleEvent("p1", "Zahnarzt", null, 1));
        googleHas(FUSSBALL, googleEvent("f1", "Training", null, 2));
        googleHas(SCHULE, googleEvent("s1", "Klassenarbeit", null, 3));

        mvc.perform(post("/api/google/sync").with(as(emma)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.lastSyncAt").isNotEmpty());
        assertThat(imported()).hasSize(2);

        mvc.perform(put("/api/google/calendars").with(as(emma)).contentType(APPLICATION_JSON).content("""
                        [{"calendarId": "%s", "enabled": false, "category": "sports"},
                         {"calendarId": "%s", "enabled": true, "category": "family"}]
                        """.formatted(FUSSBALL, SCHULE)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.calendars[2].enabled").value(true))
                .andExpect(jsonPath("$.calendars[2].category").value("family"));

        assertThat(imported()).extracting(CalendarEvent::title).containsExactlyInAnyOrder("Zahnarzt", "Klassenarbeit");
        assertThat(importedWithTitle("Klassenarbeit").category()).isEqualTo(EventCategory.FAMILY);

        mvc.perform(put("/api/google/calendars").with(as(emma)).contentType(APPLICATION_JSON)
                        .content("[{\"calendarId\": \"fremd\", \"enabled\": true, \"category\": \"family\"}]"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.calendarId").value("Unbekannter Kalender: fremd"));
        mvc.perform(post("/api/google/sync").with(as(lucas)))
                .andExpect(status().isNotFound());
    }

    @Test
    void importedEventsAreReadOnlyEvenForAdministrators() throws Exception {
        googleHas(PRIMARY, googleEvent("p1", "Zahnarzt", null, 1));
        sync.sync(connection);
        CalendarEvent imported = importedWithTitle("Zahnarzt");

        for (FamilyMember member : List.of(emma, sarah)) {
            mvc.perform(put("/api/events/" + imported.id()).with(as(member)).contentType(APPLICATION_JSON).content("""
                            {"title": "Geändert", "start": "2026-10-01T10:00", "end": "2026-10-01T11:00",
                             "memberIds": ["%s"], "category": "appointment"}
                            """.formatted(emma.id())))
                    .andExpect(status().isConflict())
                    .andExpect(jsonPath("$.detail").value(Matchers.startsWith("Google-Termine änderst du in Google.")));
            mvc.perform(delete("/api/events/" + imported.id()).with(as(member)))
                    .andExpect(status().isConflict());
        }
        mvc.perform(get("/api/events/" + imported.id()).with(as(emma)))
                .andExpect(jsonPath("$.external.provider").value("google"));
    }

    @Test
    void newEventsCannotPretendToComeFromGoogle() throws Exception {
        mvc.perform(post("/api/events").with(as(emma)).contentType(APPLICATION_JSON).content("""
                        {"title": "Eigener", "start": "2026-10-01T10:00", "end": "2026-10-01T11:00",
                         "memberIds": ["%s"], "category": "family",
                         "external": {"provider": "google", "calendarId": "x", "eventId": "y"}}
                        """.formatted(emma.id())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.external").doesNotExist());
    }

    @Test
    void importedEventsFollowTheNormalVisibilityRules() throws Exception {
        settings.save(new FamilySettings(FamilySettings.ID, Set.of(EventCategory.SPORTS)));
        googleHas(PRIMARY, googleEvent("p1", "Arzt", "private", 1), googleEvent("p2", "Kino", null, 2));
        googleHas(FUSSBALL, googleEvent("f1", "Training", null, 3));
        sync.sync(connection);

        mvc.perform(get("/api/events").with(as(lucas)))
                .andExpect(jsonPath("$[*].title", Matchers.containsInAnyOrder("Kino", "Training")));
        mvc.perform(get("/api/events").with(as(sarah)))
                .andExpect(jsonPath("$[*].title", Matchers.containsInAnyOrder("Arzt", "Kino", "Training")));
        mvc.perform(get("/api/events").with(as(oma)))
                .andExpect(jsonPath("$[*].title", Matchers.contains("Training")));
    }

    @Test
    void disconnectAndMemberDeletionRemoveImportedEvents() throws Exception {
        googleHas(PRIMARY, googleEvent("p1", "Zahnarzt", null, 1));
        sync.sync(connection);

        mvc.perform(delete("/api/google").with(as(emma))).andExpect(status().isNoContent());
        assertThat(imported()).isEmpty();

        // Nur Google-Termine blockieren das Löschen eines Mitglieds nicht
        connections.save(connection);
        sync.sync(connection);
        assertThat(imported()).hasSize(1);
        mvc.perform(delete("/api/members/" + emma.id()).with(as(sarah))).andExpect(status().isNoContent());
        assertThat(imported()).isEmpty();
        assertThat(connections.count()).isZero();
    }
}
