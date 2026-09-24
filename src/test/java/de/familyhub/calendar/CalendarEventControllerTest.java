package de.familyhub.calendar;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.contains;
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

import de.familyhub.family.FamilyMember;
import de.familyhub.family.FamilyMemberRepository;

@SpringBootTest
@AutoConfigureMockMvc
class CalendarEventControllerTest {

    private static final LocalDateTime DAY = LocalDateTime.of(2026, 9, 25, 0, 0);

    @Autowired
    private MockMvc mvc;

    @Autowired
    private CalendarEventRepository events;

    @Autowired
    private FamilyMemberRepository members;

    private FamilyMember lucas;
    private FamilyMember emma;

    @BeforeEach
    void setUp() {
        events.deleteAll();
        members.deleteAll();
        lucas = members.save(new FamilyMember(null, "Lucas", "#F97316"));
        emma = members.save(new FamilyMember(null, "Emma", "#8B5CF6"));
    }

    private static String json(String title, String start, String end, String memberId, String category) {
        return """
                {"title": "%s", "start": "%s", "end": "%s", "memberId": "%s", "category": "%s"}
                """.formatted(title, start, end, memberId, category);
    }

    private CalendarEvent save(String title, LocalDateTime start, LocalDateTime end, FamilyMember member) {
        return events.save(new CalendarEvent(null, title, start, end, member.id(), EventCategory.FAMILY, null, null));
    }

    @Test
    void createReturns201WithUiFormat() throws Exception {
        mvc.perform(post("/api/events").contentType(APPLICATION_JSON)
                        .content(json("Basketball", "2026-09-25T10:00", "2026-09-25T12:00", lucas.id(), "sports")))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", containsString("/api/events/")))
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.category").value("sports"))
                .andExpect(jsonPath("$.start").value("2026-09-25T10:00:00"));
    }

    @Test
    void createWithUnknownMemberIsRejected() throws Exception {
        mvc.perform(post("/api/events").contentType(APPLICATION_JSON)
                        .content(json("Basketball", "2026-09-25T10:00", "2026-09-25T12:00", "000000000000000000000000", "sports")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.memberId").value("Familienmitglied existiert nicht"));
    }

    @Test
    void createWithInvalidFieldsReturnsAllErrors() throws Exception {
        mvc.perform(post("/api/events").contentType(APPLICATION_JSON)
                        .content(json("", "2026-09-25T12:00", "2026-09-25T10:00", lucas.id(), "sports")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.title").value("Titel darf nicht leer sein"))
                .andExpect(jsonPath("$.errors.endAfterStart").value("Ende muss nach dem Beginn liegen"));
    }

    @Test
    void unknownCategoryIsReportedAsUnreadableRequest() throws Exception {
        mvc.perform(post("/api/events").contentType(APPLICATION_JSON)
                        .content(json("Party", "2026-09-25T10:00", "2026-09-25T12:00", lucas.id(), "party")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Anfrage nicht lesbar"));
    }

    @Test
    void listWithRangeReturnsTouchingEventsSortedByStart() throws Exception {
        save("Nacht vom Vortag", DAY.minusHours(1), DAY.plusHours(1), lucas);
        save("Training Emma", DAY.withHour(16), DAY.withHour(17), emma);
        save("Basketball", DAY.withHour(10), DAY.withHour(12), lucas);
        save("Nächster Tag", DAY.plusDays(1).withHour(10), DAY.plusDays(1).withHour(11), lucas);

        mvc.perform(get("/api/events").param("from", "2026-09-25T00:00").param("to", "2026-09-26T00:00"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].title", contains("Nacht vom Vortag", "Basketball", "Training Emma")));

        mvc.perform(get("/api/events").param("from", "2026-09-25T00:00").param("to", "2026-09-26T00:00")
                        .param("memberId", emma.id()))
                .andExpect(jsonPath("$[*].title", contains("Training Emma")));
    }

    @Test
    void listWithoutParametersReturnsAllEventsSorted() throws Exception {
        save("Später", DAY.withHour(18), DAY.withHour(19), lucas);
        save("Früher", DAY.withHour(8), DAY.withHour(9), emma);

        mvc.perform(get("/api/events"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[*].title", contains("Früher", "Später")));
    }

    @Test
    void invalidRangeParametersAreRejected() throws Exception {
        mvc.perform(get("/api/events").param("from", "2026-09-25T00:00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.to").value("from und to müssen zusammen angegeben werden"));

        mvc.perform(get("/api/events").param("from", "2026-09-26T00:00").param("to", "2026-09-25T00:00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.to").value("to muss nach from liegen"));

        mvc.perform(get("/api/events").param("from", "25.09.2026").param("to", "2026-09-26T00:00"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(containsString("from")));
    }

    @Test
    void updateChangesEventAndUnknownIdReturns404() throws Exception {
        CalendarEvent event = save("Basketball", DAY.withHour(10), DAY.withHour(12), lucas);

        mvc.perform(put("/api/events/" + event.id()).contentType(APPLICATION_JSON)
                        .content(json("Basketball-Finale", "2026-09-25T11:00", "2026-09-25T13:00", emma.id(), "sports")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(event.id()))
                .andExpect(jsonPath("$.title").value("Basketball-Finale"))
                .andExpect(jsonPath("$.memberId").value(emma.id()));

        mvc.perform(put("/api/events/000000000000000000000000").contentType(APPLICATION_JSON)
                        .content(json("X", "2026-09-25T11:00", "2026-09-25T13:00", emma.id(), "sports")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.title").value("Nicht gefunden"));
    }

    @Test
    void deleteRemovesEvent() throws Exception {
        CalendarEvent event = save("Basketball", DAY.withHour(10), DAY.withHour(12), lucas);

        mvc.perform(delete("/api/events/" + event.id())).andExpect(status().isNoContent());
        mvc.perform(get("/api/events/" + event.id())).andExpect(status().isNotFound());
    }
}
