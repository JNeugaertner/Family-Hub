package de.familyhub.google;

import static de.familyhub.testsupport.TestUsers.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;

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
import de.familyhub.calendar.EventStatus;
import de.familyhub.family.FamilyMember;
import de.familyhub.family.FamilyMemberRepository;
import de.familyhub.permission.Role;
import de.familyhub.testsupport.TestUsers;

// FamilyHub-Termine in den Google-Kalender „FamilyHub“ eintragen (Google simuliert): welche Termine, Abgleich mit
// dem, was schon in Google steht, Ein- und Ausschalten und das Nachtragen kurz nach einer Änderung.
@SpringBootTest
@AutoConfigureMockMvc
class GoogleCalendarExportTest {

    private static final String PRIMARY = "emma@example.com";
    private static final String FAMILYHUB = "familyhub@group.calendar.google.com";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private FamilyMemberRepository members;

    @Autowired
    private CalendarEventRepository events;

    @Autowired
    private GoogleConnectionRepository connections;

    @Autowired
    private TokenCipher cipher;

    @Autowired
    private GoogleCalendarSync sync;

    @Autowired
    private Clock clock;

    @MockitoBean
    private GoogleApi google;

    private FamilyMember sarah;
    private FamilyMember emma;
    private FamilyMember lucas;
    private GoogleConnection connection;
    // Mitten im Zeitfenster des Abgleichs
    private LocalDateTime start;

    @BeforeEach
    void setUp() {
        events.deleteAll();
        connections.deleteAll();
        members.deleteAll();
        sarah = members.save(TestUsers.member("Sarah", Role.ADMINISTRATOR));
        emma = members.save(TestUsers.member("Emma", Role.JUGENDLICHER));
        lucas = members.save(TestUsers.member("Lucas", Role.KIND));
        connection = connections.save(new GoogleConnection(null, emma.id(), PRIMARY, cipher.encrypt("refresh-1"),
                Instant.now(), List.of(new GoogleCalendarChoice(PRIMARY, "Emma", true, true, EventCategory.APPOINTMENT)),
                null, null, false, true, null));
        start = LocalDate.now(clock).plusDays(3).atTime(10, 0);
        when(google.refreshAccessToken("refresh-1")).thenReturn("access-1");
        when(google.createCalendar(eq("access-1"), eq("FamilyHub"), any(), any()))
                .thenReturn(new GoogleApi.CalendarInfo(FAMILYHUB, "FamilyHub", null, null));
    }

    private CalendarEvent event(String title, List<String> memberIds, boolean privateEvent, EventStatus status) {
        return events.save(new CalendarEvent(null, title, start, start.plusHours(1), memberIds, EventCategory.FAMILY,
                null, null, privateEvent, status, sarah.id(), null));
    }

    private GoogleConnection exporting() {
        return connections.save(connections.findById(connection.id()).orElseThrow().withExportCalendar(FAMILYHUB));
    }

    private GoogleEvent.Time googleTime(LocalDateTime time) {
        ZoneId zone = clock.getZone();
        return new GoogleEvent.Time(time.atZone(zone).toOffsetDateTime(), null);
    }

    // So steht ein FamilyHub-Termin in Google, nachdem FamilyHub ihn eingetragen hat
    private GoogleEvent entry(String googleId, String familyhubId, String summary) {
        return new GoogleEvent(googleId, "confirmed", summary, null, null, null, googleTime(start),
                googleTime(start.plusHours(1)),
                new GoogleEvent.ExtendedProperties(Map.of(GoogleEvent.FAMILYHUB_ID, familyhubId)));
    }

    private void familyHubCalendarHas(GoogleEvent... entries) {
        when(google.listEvents(eq("access-1"), eq(FAMILYHUB), any(), any())).thenReturn(List.of(entries));
    }

    @Test
    void enablingCreatesTheCalendarAndExportsApprovedEventsTheMemberTakesPartIn() throws Exception {
        CalendarEvent secret = event("Arzt", List.of(emma.id()), true, EventStatus.APPROVED);
        event("Nachhilfe", List.of(emma.id()), false, EventStatus.APPROVED);
        event("Familienessen", List.of(emma.id(), lucas.id()), false, EventStatus.APPROVED);
        event("Kino?", List.of(emma.id(), lucas.id()), false, EventStatus.PROPOSED);
        event("Elternabend", List.of(sarah.id()), false, EventStatus.APPROVED);
        // Kommt aus Emmas Google Kalender und wird importiert, aber nicht zurückgeschrieben
        when(google.listEvents(eq("access-1"), eq(PRIMARY), any(), any())).thenReturn(List.of(new GoogleEvent("p1",
                "confirmed", "Zahnarzt", null, null, null, googleTime(start), googleTime(start.plusHours(1)))));

        mvc.perform(put("/api/google/export").with(as(emma)).contentType(APPLICATION_JSON)
                        .content("{\"enabled\": true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exportEnabled").value(true));

        ArgumentCaptor<GoogleEventInput> written = ArgumentCaptor.forClass(GoogleEventInput.class);
        verify(google, times(3)).insertEvent(eq("access-1"), eq(FAMILYHUB), written.capture());
        assertThat(written.getAllValues()).extracting(GoogleEventInput::summary)
                .containsExactlyInAnyOrder("Arzt", "Nachhilfe", "Familienessen");
        GoogleEventInput arzt = written.getAllValues().stream().filter(e -> e.summary().equals("Arzt"))
                .findFirst().orElseThrow();
        assertThat(arzt.visibility()).isEqualTo("private");
        assertThat(arzt.extendedProperties().privateProperties()).containsEntry(GoogleEvent.FAMILYHUB_ID, secret.id());
        assertThat(OffsetDateTime.parse(arzt.start().dateTime()).atZoneSameInstant(clock.getZone()).toLocalDateTime())
                .isEqualTo(start);
        assertThat(events.findImportedByMember(emma.id())).extracting(CalendarEvent::title).containsExactly("Zahnarzt");
        assertThat(connections.findById(connection.id()).orElseThrow().exportCalendarId()).isEqualTo(FAMILYHUB);
    }

    @Test
    void enablingNeedsWriteAccessFromGoogle() throws Exception {
        connections.save(new GoogleConnection(connection.id(), emma.id(), PRIMARY, connection.encryptedRefreshToken(),
                connection.connectedAt(), connection.calendars(), null, null, false, false, null));

        mvc.perform(put("/api/google/export").with(as(emma)).contentType(APPLICATION_JSON)
                        .content("{\"enabled\": true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(
                        "Google hat das Eintragen noch nicht erlaubt. Bitte zuerst bei Google zustimmen."));
        verify(google, never()).createCalendar(any(), any(), any(), any());
    }

    @Test
    void laterRunsOverwriteChangedEntriesDeleteStaleOnesAndKeepEntriesMadeByHand() {
        CalendarEvent unchanged = event("Nachhilfe", List.of(emma.id()), false, EventStatus.APPROVED);
        CalendarEvent changed = event("Training", List.of(emma.id()), false, EventStatus.APPROVED);
        familyHubCalendarHas(
                entry("g1", unchanged.id(), "Nachhilfe"),
                entry("g2", changed.id(), "In Google umbenannt"),
                entry("g3", unchanged.id(), "Nachhilfe"),
                entry("g4", "in-familyhub-geloescht", "Alter Termin"),
                new GoogleEvent("g5", "confirmed", "Von Hand eingetragen", null, null, null, googleTime(start),
                        googleTime(start.plusHours(1))));

        GoogleConnection result = sync.sync(exporting());

        assertThat(result.lastSyncError()).isNull();
        verify(google, never()).insertEvent(any(), any(), any());
        verify(google).updateEvent(eq("access-1"), eq(FAMILYHUB), eq("g2"), argThat(e -> e.summary().equals("Training")));
        verify(google).deleteEvent("access-1", FAMILYHUB, "g3");
        verify(google).deleteEvent("access-1", FAMILYHUB, "g4");
        verify(google, never()).deleteEvent(any(), any(), eq("g1"));
        verify(google, never()).deleteEvent(any(), any(), eq("g5"));
    }

    @Test
    void changesInFamilyHubArePushedWithoutWaitingForTheNextSync() throws Exception {
        exporting();
        // Änderungen aus dem Aufbau des Tests (deleteAll, save) abarbeiten, damit nur der neue Termin zählt
        sync.pushPending();
        clearInvocations(google);

        sync.pushPending();
        verify(google, never()).listEvents(any(), any(), any(), any());

        mvc.perform(post("/api/events").with(as(emma)).contentType(APPLICATION_JSON).content("""
                        {"title": "Schwimmen", "start": "%s", "end": "%s", "memberIds": ["%s"], "category": "sports"}
                        """.formatted(start, start.plusHours(1), emma.id())))
                .andExpect(status().isCreated());
        sync.pushPending();

        verify(google).insertEvent(eq("access-1"), eq(FAMILYHUB), argThat(e -> e.summary().equals("Schwimmen")));
        // Ohne weitere Änderung kein weiterer Durchgang; der Import läuft dabei nicht mit
        sync.pushPending();
        verify(google, times(1)).listEvents(eq("access-1"), eq(FAMILYHUB), any(), any());
        verify(google, never()).listEvents(eq("access-1"), eq(PRIMARY), any(), any());
    }

    @Test
    void calendarDeletedInGoogleIsCreatedAgain() {
        event("Nachhilfe", List.of(emma.id()), false, EventStatus.APPROVED);
        when(google.listEvents(eq("access-1"), eq(FAMILYHUB), any(), any()))
                .thenThrow(GoogleException.notFound("weg"));
        when(google.createCalendar(eq("access-1"), eq("FamilyHub"), any(), any()))
                .thenReturn(new GoogleApi.CalendarInfo("neu@group.calendar.google.com", "FamilyHub", null, null));

        GoogleConnection result = sync.sync(exporting());

        assertThat(result.exportCalendarId()).isEqualTo("neu@group.calendar.google.com");
        assertThat(result.lastSyncError()).isNull();
        verify(google).insertEvent(eq("access-1"), eq("neu@group.calendar.google.com"),
                argThat(e -> e.summary().equals("Nachhilfe")));
    }

    @Test
    void disablingDeletesTheFamilyHubCalendarInGoogle() throws Exception {
        exporting();

        mvc.perform(put("/api/google/export").with(as(emma)).contentType(APPLICATION_JSON)
                        .content("{\"enabled\": false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.exportEnabled").value(false))
                .andExpect(jsonPath("$.writeGranted").value(true));

        verify(google).deleteCalendar("access-1", FAMILYHUB);
        assertThat(connections.findById(connection.id()).orElseThrow().exportCalendarId()).isNull();
    }
}
