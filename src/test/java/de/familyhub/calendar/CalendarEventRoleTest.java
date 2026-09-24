package de.familyhub.calendar;

import static de.familyhub.testsupport.TestUsers.as;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import de.familyhub.family.FamilyMember;
import de.familyhub.family.FamilyMemberRepository;
import de.familyhub.permission.Action;
import de.familyhub.permission.Module;
import de.familyhub.permission.Permission;
import de.familyhub.permission.Role;
import de.familyhub.permission.Scope;
import de.familyhub.testsupport.TestUsers;

// Rechte am Kalender je Rolle (Rollenkonzept, Entscheidungen vom 24.09.2026).
@SpringBootTest
@AutoConfigureMockMvc
class CalendarEventRoleTest {

    private static final LocalDateTime DAY = LocalDateTime.of(2026, 9, 25, 0, 0);

    @Autowired
    private MockMvc mvc;

    @Autowired
    private CalendarEventRepository events;

    @Autowired
    private FamilyMemberRepository members;

    private FamilyMember sarah;
    private FamilyMember emma;
    private FamilyMember lucas;
    private FamilyMember oma;
    private CalendarEvent lucasEvent;
    private CalendarEvent emmaEvent;

    @BeforeEach
    void setUp() {
        events.deleteAll();
        members.deleteAll();
        sarah = members.save(TestUsers.member("Sarah", Role.ADMINISTRATOR));
        emma = members.save(TestUsers.member("Emma", Role.JUGENDLICHER));
        lucas = members.save(TestUsers.member("Lucas", Role.KIND));
        oma = members.save(TestUsers.member("Oma", Role.GAST));
        lucasEvent = save("Basketball", lucas, 10);
        emmaEvent = save("Training", emma, 16);
        save("Elternabend", sarah, 19);
    }

    private CalendarEvent save(String title, FamilyMember member, int hour) {
        return events.save(new CalendarEvent(null, title, DAY.withHour(hour), DAY.withHour(hour + 1), member.id(),
                EventCategory.FAMILY, null, null));
    }

    private static String json(String title, FamilyMember member, int hour) {
        return """
                {"title": "%s", "start": "2026-09-26T%02d:00", "end": "2026-09-26T%02d:00", "memberId": "%s",
                 "category": "sports"}
                """.formatted(title, hour, hour + 1, member.id());
    }

    @Test
    void childSeesAllFamilyEventsButCannotChangeAnything() throws Exception {
        mvc.perform(get("/api/events").with(as(lucas)))
                .andExpect(jsonPath("$[*].title", containsInAnyOrder("Basketball", "Training", "Elternabend")));

        mvc.perform(post("/api/events").with(as(lucas)).contentType(APPLICATION_JSON).content(json("Kino", lucas, 15)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Keine Berechtigung, Termine anzulegen."));
        mvc.perform(put("/api/events/" + lucasEvent.id()).with(as(lucas)).contentType(APPLICATION_JSON)
                        .content(json("Basketball", lucas, 11)))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/events/" + lucasEvent.id()).with(as(lucas))).andExpect(status().isForbidden());
    }

    @Test
    void teenagerManagesOwnEvents() throws Exception {
        mvc.perform(post("/api/events").with(as(emma)).contentType(APPLICATION_JSON).content(json("Kino", emma, 15)))
                .andExpect(status().isCreated());
        mvc.perform(put("/api/events/" + emmaEvent.id()).with(as(emma)).contentType(APPLICATION_JSON)
                        .content(json("Training (verlegt)", emma, 17)))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/events/" + emmaEvent.id()).with(as(emma))).andExpect(status().isNoContent());
    }

    @Test
    void teenagerCannotChangeEventsOfOthers() throws Exception {
        mvc.perform(put("/api/events/" + lucasEvent.id()).with(as(emma)).contentType(APPLICATION_JSON)
                        .content(json("Basketball", lucas, 11)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Du darfst nur deine eigenen Termine ändern."));
        mvc.perform(delete("/api/events/" + lucasEvent.id()).with(as(emma)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.detail").value("Du darfst nur deine eigenen Termine löschen."));
        // eigenen Termin einer anderen Person zuschieben
        mvc.perform(put("/api/events/" + emmaEvent.id()).with(as(emma)).contentType(APPLICATION_JSON)
                        .content(json("Training", lucas, 16)))
                .andExpect(status().isForbidden());
    }

    @Test
    void guestSeesNothingThatWasNotReleased() throws Exception {
        mvc.perform(get("/api/events").with(as(oma))).andExpect(jsonPath("$", empty()));
        mvc.perform(get("/api/events/" + lucasEvent.id()).with(as(oma))).andExpect(status().isNotFound());
        mvc.perform(post("/api/events").with(as(oma)).contentType(APPLICATION_JSON).content(json("Besuch", oma, 15)))
                .andExpect(status().isForbidden());
    }

    @Test
    void administratorManagesAllEvents() throws Exception {
        mvc.perform(post("/api/events").with(as(sarah)).contentType(APPLICATION_JSON).content(json("Arzt", lucas, 9)))
                .andExpect(status().isCreated());
        mvc.perform(put("/api/events/" + emmaEvent.id()).with(as(sarah)).contentType(APPLICATION_JSON)
                        .content(json("Training", emma, 18)))
                .andExpect(status().isOk());
        mvc.perform(delete("/api/events/" + lucasEvent.id()).with(as(sarah))).andExpect(status().isNoContent());
    }

    @Test
    void extraPermissionLetsChildCreateOwnEvents() throws Exception {
        FamilyMember lucasWithRight = members.save(withPermissions(lucas,
                Set.of(Permission.of(Module.KALENDER, Action.ERSTELLEN, Scope.EIGEN)), Set.of()));

        mvc.perform(post("/api/events").with(as(lucasWithRight)).contentType(APPLICATION_JSON)
                        .content(json("Kino", lucas, 15)))
                .andExpect(status().isCreated());
    }

    @Test
    void revokedPermissionHidesTheCalendar() throws Exception {
        FamilyMember lucasWithoutCalendar = members.save(withPermissions(lucas, Set.of(),
                Set.of(Permission.of(Module.KALENDER, Action.ANSEHEN, Scope.FAMILIE))));

        mvc.perform(get("/api/events").with(as(lucasWithoutCalendar))).andExpect(jsonPath("$", empty()));
    }

    private static FamilyMember withPermissions(FamilyMember m, Set<Permission> extra, Set<Permission> revoked) {
        return new FamilyMember(m.id(), m.name(), m.color(), m.username(), m.passwordHash(), m.role(), m.birthDate(),
                m.roleFixed(), extra, revoked);
    }
}
