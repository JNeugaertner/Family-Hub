package de.familyhub.calendar;

import static de.familyhub.testsupport.TestUsers.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.bson.Document;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.jayway.jsonpath.JsonPath;
import com.mongodb.client.model.Filters;

import de.familyhub.family.FamilyMember;
import de.familyhub.family.FamilyMemberRepository;
import de.familyhub.permission.Role;
import de.familyhub.testsupport.TestUsers;

// Termine mit mehreren Beteiligten (Entscheidungen vom 29.09.2026): Eltern legen sie direkt an, Jugendliche nur als
// Vorschlag; private gemeinsame Termine sehen alle Beteiligten; beim Löschen eines Mitglieds wird es aus gemeinsamen
// Terminen ausgetragen.
@SpringBootTest
@AutoConfigureMockMvc
class SharedEventTest {

    private static final LocalDateTime DAY = LocalDateTime.of(2026, 9, 25, 0, 0);

    @Autowired
    private MockMvc mvc;

    @Autowired
    private CalendarEventRepository events;

    @Autowired
    private FamilyMemberRepository members;

    @Autowired
    private MongoTemplate mongo;

    @Autowired
    private EventMembersMigration migration;

    private FamilyMember sarah;
    private FamilyMember emma;
    private FamilyMember lucas;
    private FamilyMember oma;

    @BeforeEach
    void setUp() {
        events.deleteAll();
        members.deleteAll();
        sarah = members.save(TestUsers.member("Sarah", Role.ADMINISTRATOR));
        emma = members.save(TestUsers.member("Emma", Role.JUGENDLICHER));
        lucas = members.save(TestUsers.member("Lucas", Role.KIND));
        oma = members.save(TestUsers.member("Oma", Role.GAST));
    }

    private static String json(String title, boolean privateEvent, FamilyMember... who) {
        String ids = Arrays.stream(who).map(m -> '"' + m.id() + '"').collect(Collectors.joining(", "));
        return """
                {"title": "%s", "start": "2026-09-26T18:00", "end": "2026-09-26T20:00", "memberIds": [%s],
                 "category": "family", "private": %s}
                """.formatted(title, ids, privateEvent);
    }

    private String create(FamilyMember by, String body) throws Exception {
        String response = mvc.perform(post("/api/events").with(as(by)).contentType(APPLICATION_JSON).content(body))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(response, "$.id");
    }

    private CalendarEvent saved(String title, FamilyMember... who) {
        return events.save(new CalendarEvent(null, title, DAY.withHour(18), DAY.withHour(20),
                Arrays.stream(who).map(FamilyMember::id).toList(), EventCategory.FAMILY, null, null, false,
                EventStatus.APPROVED, sarah.id(), null));
    }

    @Test
    void parentCreatesEventForSeveralMembersAndFilterFindsItForEach() throws Exception {
        mvc.perform(post("/api/events").with(as(sarah)).contentType(APPLICATION_JSON)
                        .content(json("Familienessen", false, sarah, emma, lucas, emma)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("approved"))
                .andExpect(jsonPath("$.memberIds", contains(sarah.id(), emma.id(), lucas.id())));

        mvc.perform(get("/api/events").param("memberId", lucas.id()).with(as(sarah)))
                .andExpect(jsonPath("$[*].title", contains("Familienessen")));
        mvc.perform(get("/api/events").param("memberId", emma.id())
                        .param("from", "2026-09-26T00:00").param("to", "2026-09-27T00:00").with(as(sarah)))
                .andExpect(jsonPath("$[*].title", contains("Familienessen")));
        mvc.perform(get("/api/events").param("memberId", oma.id()).with(as(sarah)))
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void teenagerJointEventBecomesProposalOwnEventDoesNot() throws Exception {
        mvc.perform(post("/api/events").with(as(emma)).contentType(APPLICATION_JSON)
                        .content(json("Kino mit Lucas", false, emma, lucas)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("proposed"));
        mvc.perform(post("/api/events").with(as(emma)).contentType(APPLICATION_JSON)
                        .content(json("Training", false, emma)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("approved"));
    }

    @Test
    void teenagerCannotChangeSharedEventsOrAddOthersToOwnEvents() throws Exception {
        CalendarEvent shared = saved("Ausflug", sarah, emma);
        CalendarEvent own = saved("Training", emma);

        mvc.perform(put("/api/events/" + shared.id()).with(as(emma)).contentType(APPLICATION_JSON)
                        .content(json("Ausflug (verlegt)", false, sarah, emma)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Du darfst nur deine eigenen Termine ändern."));
        mvc.perform(delete("/api/events/" + shared.id()).with(as(emma))).andExpect(status().isForbidden());
        mvc.perform(put("/api/events/" + own.id()).with(as(emma)).contentType(APPLICATION_JSON)
                        .content(json("Training", false, emma, lucas)))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/events/" + own.id()).with(as(emma)).contentType(APPLICATION_JSON)
                        .content(json("Training (länger)", false, emma)))
                .andExpect(status().isOk());
    }

    @Test
    void privateSharedEventIsVisibleToAllParticipants() throws Exception {
        String geheim = create(sarah, json("Geschenk kaufen", true, sarah, emma));
        String arzt = create(sarah, json("Arzt", true, emma, lucas));

        mvc.perform(get("/api/events").with(as(emma)))
                .andExpect(jsonPath("$[*].id", containsInAnyOrder(geheim, arzt)));
        mvc.perform(get("/api/events").with(as(lucas)))
                .andExpect(jsonPath("$[*].id", contains(arzt)));
        mvc.perform(get("/api/events").with(as(oma)))
                .andExpect(jsonPath("$").isEmpty());
    }

    @Test
    void eventNeedsAtLeastOneExistingMember() throws Exception {
        mvc.perform(post("/api/events").with(as(sarah)).contentType(APPLICATION_JSON).content(json("Leer", false)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.memberIds").exists());
        mvc.perform(post("/api/events").with(as(sarah)).contentType(APPLICATION_JSON)
                        .content(json("Unbekannt", false, sarah).replace(sarah.id(), "gibt-es-nicht")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.memberIds").value("Familienmitglied existiert nicht"));
    }

    @Test
    void deletingMemberRemovesThemFromSharedEventsButNotFromTheirOwn() throws Exception {
        CalendarEvent shared = saved("Radtour", sarah, lucas);
        CalendarEvent own = saved("Basketball", lucas);

        mvc.perform(delete("/api/members/" + lucas.id()).with(as(sarah)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(org.hamcrest.Matchers.startsWith("Das Familienmitglied hat noch einen Termin.")));

        events.deleteById(own.id());
        mvc.perform(delete("/api/members/" + lucas.id()).with(as(sarah))).andExpect(status().isNoContent());
        assertThat(events.findById(shared.id()).orElseThrow().memberIds()).containsExactly(sarah.id());
    }

    @Test
    void migrationTurnsOldSingleMemberIntoList() {
        CalendarEvent old = saved("Alter Termin", emma);
        var collection = mongo.getCollection(mongo.getCollectionName(CalendarEvent.class));
        collection.updateOne(Filters.eq("_id", new org.bson.types.ObjectId(old.id())),
                List.of(new Document("$set", new Document("memberId", emma.id())), new Document("$unset", "memberIds")));
        assertThat(events.findById(old.id()).orElseThrow().memberIds()).isEmpty();

        assertThat(migration.migrate()).isEqualTo(1);
        assertThat(events.findById(old.id()).orElseThrow().memberIds()).containsExactly(emma.id());
        assertThat(migration.migrate()).isZero();
    }
}
