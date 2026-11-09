package de.familyhub.calendar;

import static de.familyhub.testsupport.TestUsers.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.Period;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;

import de.familyhub.family.FamilyMember;
import de.familyhub.family.FamilyMemberRepository;
import de.familyhub.permission.Role;
import de.familyhub.testsupport.TestUsers;

// Terminserien (Entscheidung vom 03.11.2026): Die Serie legt ihre Termine als normale Termine bis zum Vorlauf an.
// Ändern und Löschen gibt es für "nur diesen" und "diesen und alle folgenden"; einzeln geänderte Termine lassen
// Änderungen an der Serie in Ruhe, einzeln gelöschte kommen nicht wieder.
@SpringBootTest
@AutoConfigureMockMvc
class EventSeriesTest {

    private static final DateTimeFormatter MINUTES = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm");
    private static final String WEEKLY = "{\"frequency\": \"weekly\"}";

    @Autowired
    private MockMvc mvc;

    @Autowired
    private CalendarEventRepository events;

    @Autowired
    private EventSeriesRepository series;

    @Autowired
    private FamilyMemberRepository members;

    @Autowired
    private EventSeriesService service;

    @Autowired
    private CalendarProperties properties;

    private FamilyMember sarah;
    private FamilyMember emma;
    private FamilyMember lucas;

    // Erster Termin: ein Dienstag in ein bis zwei Wochen, 17:00 bis 18:30
    private final LocalDate firstDay = LocalDate.now().plusWeeks(1).with(TemporalAdjusters.nextOrSame(DayOfWeek.TUESDAY));
    private final LocalDateTime start = firstDay.atTime(17, 0);

    @BeforeEach
    void setUp() {
        events.deleteAll();
        series.deleteAll();
        members.deleteAll();
        sarah = members.save(TestUsers.member("Sarah", Role.ADMINISTRATOR));
        emma = members.save(TestUsers.member("Emma", Role.JUGENDLICHER));
        lucas = members.save(TestUsers.member("Lucas", Role.KIND));
    }

    private static String json(String title, LocalDateTime start, String recurrence, FamilyMember... who) {
        String ids = Arrays.stream(who).map(m -> '"' + m.id() + '"').collect(Collectors.joining(", "));
        return """
                {"title": "%s", "start": "%s", "end": "%s", "memberIds": [%s], "category": "sports",
                 "location": "Sportplatz", "recurrence": %s}
                """.formatted(title, start.format(MINUTES), start.plusMinutes(90).format(MINUTES), ids, recurrence);
    }

    private String create(FamilyMember by, String body) throws Exception {
        String response = mvc.perform(post("/api/events").with(as(by)).contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    private List<CalendarEvent> all() {
        return events.findAll().stream().sorted(Comparator.comparing(CalendarEvent::start)).toList();
    }

    private static List<LocalDate> days(List<CalendarEvent> list) {
        return list.stream().map(event -> event.start().toLocalDate()).toList();
    }

    private LocalDate horizon() {
        return LocalDate.now().plus(properties.seriesHorizon());
    }

    @Test
    void weeklySeriesHasATermEveryWeekUpToTheHorizon() throws Exception {
        mvc.perform(post("/api/events").with(as(sarah)).contentType(APPLICATION_JSON)
                        .content(json("Fußball", start, WEEKLY, lucas)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.recurrence.frequency").value("weekly"))
                .andExpect(jsonPath("$.series.date").value(firstDay.toString()))
                .andExpect(jsonPath("$.series.changed").value(false));

        List<CalendarEvent> all = all();
        assertThat(days(all)).containsExactlyElementsOf(
                firstDay.datesUntil(horizon().plusDays(1), Period.ofWeeks(1)).toList());
        assertThat(all).allSatisfy(event -> {
            assertThat(event.start().toLocalTime()).isEqualTo(LocalTime.of(17, 0));
            assertThat(Duration.between(event.start(), event.end())).isEqualTo(Duration.ofMinutes(90));
            assertThat(event.memberIds()).containsExactly(lucas.id());
            assertThat(event.location()).isEqualTo("Sportplatz");
            assertThat(event.series().seriesId()).isEqualTo(all.get(0).series().seriesId());
        });

        // Ein Zeitraum findet den Serientermin wie jeden anderen Termin
        LocalDate third = firstDay.plusWeeks(2);
        mvc.perform(get("/api/events").with(as(lucas))
                        .param("from", third.atStartOfDay().toString()).param("to", third.plusDays(1).atStartOfDay().toString()))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].title").value("Fußball"));
    }

    @Test
    void countAndUntilEndTheSeries() throws Exception {
        create(sarah, json("Schwimmkurs", start, "{\"frequency\": \"weekly\", \"count\": 5}", lucas));
        create(sarah, json("Ferienprogramm", start, "{\"frequency\": \"daily\", \"until\": \"%s\"}"
                .formatted(firstDay.plusDays(2)), lucas));
        create(sarah, json("Geige", start, "{\"frequency\": \"weekly\", \"interval\": 2, "
                + "\"weekdays\": [\"TUESDAY\", \"THURSDAY\"], \"count\": 4}", lucas));

        assertThat(all()).filteredOn(event -> event.title().equals("Schwimmkurs")).hasSize(5);
        assertThat(days(all().stream().filter(event -> event.title().equals("Ferienprogramm")).toList()))
                .containsExactly(firstDay, firstDay.plusDays(1), firstDay.plusDays(2));
        assertThat(days(all().stream().filter(event -> event.title().equals("Geige")).toList()))
                .containsExactly(firstDay, firstDay.plusDays(2), firstDay.plusWeeks(2), firstDay.plusWeeks(2).plusDays(2));
    }

    @Test
    void singleChangeStaysWhenTheWholeSeriesChanges() throws Exception {
        create(sarah, json("Fußball", start, WEEKLY, lucas));
        List<CalendarEvent> before = all();
        CalendarEvent second = before.get(1);

        // Nur dieser Termin (Standard): eine halbe Stunde später
        mvc.perform(put("/api/events/" + second.id()).with(as(sarah)).contentType(APPLICATION_JSON)
                        .content(json("Fußball", second.start().plusMinutes(30), WEEKLY, lucas)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.series.changed").value(true))
                .andExpect(jsonPath("$.recurrence.frequency").value("weekly"));

        // Ab dem ersten Termin, also die ganze Serie: 18 Uhr
        mvc.perform(put("/api/events/" + before.get(0).id()).param("scope", "following").with(as(sarah))
                        .contentType(APPLICATION_JSON).content(json("Fußball", start.withHour(18), WEEKLY, lucas)))
                .andExpect(status().isOk());

        List<CalendarEvent> after = all();
        assertThat(after).hasSameSizeAs(before);
        assertThat(after).filteredOn(event -> !event.series().changed())
                .allSatisfy(event -> assertThat(event.start().getHour()).isEqualTo(18));
        assertThat(after).filteredOn(event -> event.series().changed()).singleElement()
                .satisfies(event -> assertThat(event.start()).isEqualTo(second.start().plusMinutes(30)));
        assertThat(series.findAll()).hasSize(1);
    }

    @Test
    void changingFollowingFromTheMiddleSplitsTheSeries() throws Exception {
        create(sarah, json("Fußball", start, WEEKLY, lucas));
        List<CalendarEvent> before = all();
        CalendarEvent third = before.get(2);
        String oldSeries = third.series().seriesId();

        mvc.perform(put("/api/events/" + third.id()).param("scope", "following").with(as(sarah))
                        .contentType(APPLICATION_JSON).content(json("Fußball", third.start().withHour(18), WEEKLY, lucas)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.start").value(third.start().withHour(18).toString() + ":00"));

        List<CalendarEvent> after = all();
        assertThat(days(after)).containsExactlyElementsOf(days(before));
        assertThat(after.subList(0, 2)).allSatisfy(event -> {
            assertThat(event.start().getHour()).isEqualTo(17);
            assertThat(event.series().seriesId()).isEqualTo(oldSeries);
        });
        assertThat(after.subList(2, after.size())).allSatisfy(event -> {
            assertThat(event.start().getHour()).isEqualTo(18);
            assertThat(event.series().seriesId()).isNotEqualTo(oldSeries);
        });
        assertThat(series.findById(oldSeries)).get()
                .satisfies(old -> assertThat(old.recurrence().until()).isEqualTo(third.start().toLocalDate().minusDays(1)));
    }

    @Test
    void changingFollowingWithoutRecurrenceEndsTheSeriesHere() throws Exception {
        create(sarah, json("Fußball", start, WEEKLY, lucas));
        CalendarEvent third = all().get(2);

        mvc.perform(put("/api/events/" + third.id()).param("scope", "following").with(as(sarah))
                        .contentType(APPLICATION_JSON).content(json("Abschlussspiel", third.start(), "null", lucas)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(third.id()))
                .andExpect(jsonPath("$.series").doesNotExist());

        List<CalendarEvent> after = all();
        assertThat(after).hasSize(3);
        assertThat(after.get(2)).satisfies(event -> {
            assertThat(event.title()).isEqualTo("Abschlussspiel");
            assertThat(event.isSeries()).isFalse();
            assertThat(event.recurrence()).isNull();
        });
    }

    @Test
    void aSingleDeletedTermDoesNotComeBack() throws Exception {
        create(sarah, json("Fußball", start, WEEKLY, lucas));
        List<CalendarEvent> before = all();
        LocalDate holiday = before.get(1).start().toLocalDate();

        mvc.perform(delete("/api/events/" + before.get(1).id()).with(as(sarah))).andExpect(status().isNoContent());
        // Auch nicht, wenn die ganze Serie geändert oder verlängert wird
        mvc.perform(put("/api/events/" + before.get(0).id()).param("scope", "following").with(as(sarah))
                        .contentType(APPLICATION_JSON).content(json("Fußball", start.withHour(18), WEEKLY, lucas)))
                .andExpect(status().isOk());
        service.extendAll();

        assertThat(days(all())).hasSize(before.size() - 1).doesNotContain(holiday);
    }

    @Test
    void deletingFollowingEndsTheSeriesAndFromTheFirstTermRemovesIt() throws Exception {
        create(sarah, json("Fußball", start, WEEKLY, lucas));
        List<CalendarEvent> before = all();

        mvc.perform(delete("/api/events/" + before.get(2).id()).param("scope", "following").with(as(sarah)))
                .andExpect(status().isNoContent());
        assertThat(days(all())).containsExactly(days(before).get(0), days(before).get(1));
        service.extendAll();
        assertThat(all()).hasSize(2);

        mvc.perform(delete("/api/events/" + before.get(0).id()).param("scope", "following").with(as(sarah)))
                .andExpect(status().isNoContent());
        assertThat(all()).isEmpty();
        assertThat(series.findAll()).isEmpty();
    }

    // Jugendliche schlagen gemeinsame Serien vor: Bis zur Freigabe gibt es nur den ersten Termin
    @Test
    void teenagerSeriesWithOthersIsOneProposalUntilApproved() throws Exception {
        String proposal = create(emma, json("Kino-Abo", start, WEEKLY, emma, lucas));
        assertThat(all()).singleElement().satisfies(event -> assertThat(event.isProposal()).isTrue());

        // Ändert Emma ihren Vorschlag, gilt das für die ganze Serie
        mvc.perform(put("/api/events/" + proposal).with(as(emma)).contentType(APPLICATION_JSON)
                        .content(json("Kino-Abo", start.withHour(19), WEEKLY, emma, lucas)))
                .andExpect(status().isOk());
        assertThat(all()).singleElement().satisfies(event -> assertThat(event.start().getHour()).isEqualTo(19));
        String approvedId = all().get(0).id();

        mvc.perform(post("/api/events/" + approvedId + "/approve").with(as(sarah)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("approved"));
        assertThat(all()).hasSizeGreaterThan(40).allSatisfy(event -> {
            assertThat(event.isProposal()).isFalse();
            assertThat(event.start().getHour()).isEqualTo(19);
        });

        String rejected = create(emma, json("Schwimmen", start, WEEKLY, emma, lucas));
        mvc.perform(post("/api/events/" + rejected + "/reject").with(as(sarah))).andExpect(status().isNoContent());
        assertThat(all()).noneMatch(event -> event.title().equals("Schwimmen"));
        assertThat(series.findAll()).hasSize(1);
    }

    @Test
    void aSingleEventBecomesASeries() throws Exception {
        String single = create(sarah, json("Fußball", start, "null", lucas));

        mvc.perform(put("/api/events/" + single).with(as(sarah)).contentType(APPLICATION_JSON)
                        .content(json("Fußball", start, WEEKLY, lucas)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.series.date").value(firstDay.toString()));

        assertThat(events.findById(single)).isEmpty();
        assertThat(all()).hasSizeGreaterThan(40).first()
                .satisfies(event -> assertThat(event.start()).isEqualTo(start));
    }

    @Test
    void contradictoryOrEmptyRulesAreRejected() throws Exception {
        mvc.perform(post("/api/events").with(as(sarah)).contentType(APPLICATION_JSON)
                        .content(json("A", start, "{\"frequency\": \"weekly\", \"until\": \"%s\"}"
                                .formatted(firstDay.minusDays(1)), lucas)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.recurrenceEndAfterStart").value("Die Wiederholung muss nach dem Beginn enden"));
        mvc.perform(post("/api/events").with(as(sarah)).contentType(APPLICATION_JSON)
                        .content(json("B", start, "{\"frequency\": \"monthly\", \"weekdays\": [\"TUESDAY\"]}", lucas)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors['recurrence.weekdaysOnlyWeekly']").exists());
        mvc.perform(post("/api/events").with(as(sarah)).contentType(APPLICATION_JSON)
                        .content(json("C", start, "{\"frequency\": \"daily\", \"until\": \"%s\", \"count\": 3}"
                                .formatted(firstDay.plusDays(5)), lucas)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors['recurrence.endUnambiguous']").exists());
        mvc.perform(post("/api/events").with(as(sarah)).contentType(APPLICATION_JSON)
                        .content(json("D", start, "{\"frequency\": \"weekly\", \"interval\": 0}", lucas)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors['recurrence.interval']").exists());
        // Dienstag als Beginn, nur freitags, Ende schon am Donnerstag: kein einziger Termin
        mvc.perform(post("/api/events").with(as(sarah)).contentType(APPLICATION_JSON)
                        .content(json("E", start, "{\"frequency\": \"weekly\", \"weekdays\": [\"FRIDAY\"], \"until\": \"%s\"}"
                                .formatted(firstDay.plusDays(2)), lucas)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.recurrence").value("Die Wiederholung ergibt keinen einzigen Termin"));

        assertThat(all()).isEmpty();
        assertThat(series.findAll()).isEmpty();
    }

    @Test
    void extendingAddsMissingTermsOnceAndKeepsSkippedDaysOut() throws Exception {
        create(sarah, json("Fußball", start, WEEKLY, lucas));
        List<CalendarEvent> before = all();
        // So tun, als wären die letzten vier Termine noch nicht angelegt und einer davon einzeln gelöscht worden
        List<CalendarEvent> lastFour = before.subList(before.size() - 4, before.size());
        events.deleteAll(lastFour);
        EventSeries current = series.findAll().get(0);
        series.save(current.withGeneratedUntil(lastFour.get(0).start().toLocalDate().minusDays(1))
                .skipping(lastFour.get(1).series().date()));

        service.extendAll();
        service.extendAll();

        assertThat(days(all())).hasSize(before.size() - 1).doesNotHaveDuplicates()
                .doesNotContain(lastFour.get(1).start().toLocalDate())
                .contains(lastFour.get(3).start().toLocalDate());
    }

    @Test
    void deletingAMemberRemovesThemFromTheSeries() throws Exception {
        create(sarah, json("Familienkochen", start, WEEKLY, sarah, lucas));

        mvc.perform(delete("/api/members/" + lucas.id()).with(as(sarah))).andExpect(status().isNoContent());
        service.extendAll();

        assertThat(all()).isNotEmpty().allSatisfy(event -> assertThat(event.memberIds()).containsExactly(sarah.id()));
        assertThat(series.findAll()).singleElement()
                .satisfies(current -> assertThat(current.template().memberIds()).containsExactly(sarah.id()));
    }
}
