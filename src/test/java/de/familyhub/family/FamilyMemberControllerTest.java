package de.familyhub.family;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import de.familyhub.calendar.CalendarEvent;
import de.familyhub.calendar.CalendarEventRepository;
import de.familyhub.calendar.EventCategory;

@SpringBootTest
@AutoConfigureMockMvc
class FamilyMemberControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private FamilyMemberRepository members;

    @Autowired
    private CalendarEventRepository events;

    @BeforeEach
    void cleanDatabase() {
        events.deleteAll();
        members.deleteAll();
    }

    @Test
    void createReturns201WithLocationAndIgnoresSentId() throws Exception {
        mvc.perform(post("/api/members").contentType(APPLICATION_JSON).content("""
                        {"id": "eigene-id", "name": "Sarah", "color": "#2563EB"}
                        """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", matchesPattern(".*/api/members/[0-9a-f]{24}")))
                .andExpect(jsonPath("$.id").value(not("eigene-id")))
                .andExpect(jsonPath("$.name").value("Sarah"))
                .andExpect(jsonPath("$.color").value("#2563EB"));
    }

    @Test
    void invalidInputReturnsGermanFieldErrors() throws Exception {
        mvc.perform(post("/api/members").contentType(APPLICATION_JSON).content("""
                        {"name": "", "color": "blau"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Ungültige Eingaben"))
                .andExpect(jsonPath("$.errors.name").value("Name darf nicht leer sein"))
                .andExpect(jsonPath("$.errors.color").value("Farbe muss ein Hex-Wert wie #2563EB sein"));
    }

    @Test
    void listAndGetReturnStoredMembers() throws Exception {
        FamilyMember sarah = members.save(new FamilyMember(null, "Sarah", "#2563EB"));
        members.save(new FamilyMember(null, "Mike", "#14B8A6"));

        mvc.perform(get("/api/members"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
        mvc.perform(get("/api/members/" + sarah.id()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Sarah"));
    }

    @Test
    void updateChangesMember() throws Exception {
        FamilyMember sarah = members.save(new FamilyMember(null, "Sarah", "#2563EB"));

        mvc.perform(put("/api/members/" + sarah.id()).contentType(APPLICATION_JSON).content("""
                        {"name": "Sarah M.", "color": "#1D4ED8"}
                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(sarah.id()))
                .andExpect(jsonPath("$.name").value("Sarah M."));
    }

    @Test
    void unknownMemberReturns404() throws Exception {
        mvc.perform(get("/api/members/000000000000000000000000"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Nicht gefunden"));
        mvc.perform(put("/api/members/000000000000000000000000").contentType(APPLICATION_JSON).content("""
                        {"name": "Niemand", "color": "#000000"}
                        """))
                .andExpect(status().isNotFound());
    }

    @Test
    void deleteRemovesMemberWithoutEvents() throws Exception {
        FamilyMember mike = members.save(new FamilyMember(null, "Mike", "#14B8A6"));

        mvc.perform(delete("/api/members/" + mike.id())).andExpect(status().isNoContent());
        mvc.perform(get("/api/members/" + mike.id())).andExpect(status().isNotFound());
    }

    @Test
    void deleteIsRefusedWhileMemberHasEvents() throws Exception {
        FamilyMember lucas = members.save(new FamilyMember(null, "Lucas", "#F97316"));
        LocalDateTime start = LocalDateTime.of(2026, 9, 25, 10, 0);
        events.save(new CalendarEvent(null, "Basketball", start, start.plusHours(2), lucas.id(),
                EventCategory.SPORTS, null, null));

        mvc.perform(delete("/api/members/" + lucas.id()))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(containsString("einen Termin")));
        mvc.perform(get("/api/members/" + lucas.id())).andExpect(status().isOk());
    }
}
