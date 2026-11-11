package de.familyhub.calendar;

import static de.familyhub.testsupport.TestUsers.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import de.familyhub.family.FamilyMember;
import de.familyhub.family.FamilyMemberRepository;
import de.familyhub.permission.Role;
import de.familyhub.testsupport.TestUsers;

@SpringBootTest
@AutoConfigureMockMvc
class EventTemplateControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private EventTemplateRepository templates;

    @Autowired
    private FamilyMemberRepository members;

    private FamilyMember sarah;
    private FamilyMember mike;
    private FamilyMember emma;
    private FamilyMember lucas;
    private FamilyMember oma;

    @BeforeEach
    void setUp() {
        templates.deleteAll();
        members.deleteAll();
        sarah = members.save(TestUsers.member("Sarah", Role.ADMINISTRATOR));
        mike = members.save(TestUsers.member("Mike", Role.ADMINISTRATOR));
        emma = members.save(TestUsers.member("Emma", Role.JUGENDLICHER));
        lucas = members.save(TestUsers.member("Lucas", Role.KIND));
        oma = members.save(TestUsers.member("Oma", Role.GAST));
    }

    private static String zahnarzt(String name, boolean privat) {
        return """
                {"name": "%s", "title": "Zahnarzt", "startTime": "08:30", "durationMinutes": 45,
                 "memberIds": [], "category": "appointment", "location": " Praxis Dr. Weber ",
                 "lat": 48.1, "lon": 11.5, "transportMode": "cycling-regular", "travelTime": 600,
                 "description": "Bonusheft mitnehmen", "private": %s}
                """.formatted(name, privat);
    }

    private EventTemplate saved(String name, FamilyMember creator, boolean privat) {
        return templates.save(new EventTemplate(null, name, "Termin", "10:00", 60, List.of(creator.id()),
                EventCategory.APPOINTMENT, null, null, null, null, null, null, privat, creator.id(),
                LocalDateTime.of(2026, 11, 1, 9, 0)));
    }

    @Test
    void adminCreatesTemplateWithAllFields() throws Exception {
        mvc.perform(post("/api/event-templates").with(as(sarah)).contentType(APPLICATION_JSON)
                        .content(zahnarzt("Zahnarzt Emma", false)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.startTime").value("08:30"))
                .andExpect(jsonPath("$.durationMinutes").value(45))
                .andExpect(jsonPath("$.location").value("Praxis Dr. Weber"))
                .andExpect(jsonPath("$.transportMode").value("cycling-regular"))
                .andExpect(jsonPath("$.private").value(false))
                .andExpect(jsonPath("$.createdBy").value(sarah.id()));

        assertThat(templates.findAll()).singleElement().satisfies(t -> {
            assertThat(t.travelTime()).isEqualTo(600.0);
            assertThat(t.createdAt()).isNotNull();
        });
    }

    @Test
    void invalidValuesAreRejectedPerField() throws Exception {
        mvc.perform(post("/api/event-templates").with(as(sarah)).contentType(APPLICATION_JSON).content("""
                        {"name": " ", "title": "Arzt", "startTime": "8:30", "durationMinutes": 2,
                         "category": "appointment", "transportMode": "rakete"}
                        """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.startTime").exists())
                .andExpect(jsonPath("$.errors.durationMinutes").exists())
                .andExpect(jsonPath("$.errors.transportMode").exists());
    }

    @Test
    void listShowsSharedTemplatesSortedAndPrivateOnlyToCreator() throws Exception {
        saved("Zahnarzt", sarah, false);
        saved("augenarzt", mike, false);
        saved("Therapie Sarah", sarah, true);

        mvc.perform(get("/api/event-templates").with(as(sarah)))
                .andExpect(jsonPath("$[*].name", contains("augenarzt", "Therapie Sarah", "Zahnarzt")));
        mvc.perform(get("/api/event-templates").with(as(mike)))
                .andExpect(jsonPath("$[*].name", contains("augenarzt", "Zahnarzt")));
        // Jugendliche legen eigene Termine an und schlagen gemeinsame vor: Sie nutzen die Vorlagen auch
        mvc.perform(get("/api/event-templates").with(as(emma)))
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    void childrenAndGuestsHaveNoTemplates() throws Exception {
        saved("Zahnarzt", sarah, false);
        for (FamilyMember member : List.of(lucas, oma)) {
            mvc.perform(get("/api/event-templates").with(as(member))).andExpect(status().isForbidden());
            mvc.perform(post("/api/event-templates").with(as(member)).contentType(APPLICATION_JSON)
                    .content(zahnarzt("Probe", false))).andExpect(status().isForbidden());
        }
    }

    @Test
    void creatorOrAdminMayChangeOthersMayNot() throws Exception {
        EventTemplate emmas = saved("Gitarre", emma, false);
        EventTemplate sarahs = saved("Zahnarzt", sarah, false);

        mvc.perform(put("/api/event-templates/" + emmas.id()).with(as(emma)).contentType(APPLICATION_JSON)
                        .content(zahnarzt("Gitarre neu", false)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Gitarre neu"))
                .andExpect(jsonPath("$.createdBy").value(emma.id()));
        // Administratoren dürfen Vorlagen anderer ändern, Jugendliche nicht
        mvc.perform(put("/api/event-templates/" + emmas.id()).with(as(mike)).contentType(APPLICATION_JSON)
                .content(zahnarzt("Gitarre (Mike)", false))).andExpect(status().isOk());
        mvc.perform(put("/api/event-templates/" + sarahs.id()).with(as(emma)).contentType(APPLICATION_JSON)
                .content(zahnarzt("Zahnarzt (Emma)", false))).andExpect(status().isForbidden());
        mvc.perform(delete("/api/event-templates/" + sarahs.id()).with(as(emma))).andExpect(status().isForbidden());

        mvc.perform(delete("/api/event-templates/" + sarahs.id()).with(as(mike))).andExpect(status().isNoContent());
        assertThat(templates.findById(sarahs.id())).isEmpty();
    }

    @Test
    void privateTemplatesOfOthersBehaveAsIfMissing() throws Exception {
        EventTemplate therapie = saved("Therapie Sarah", sarah, true);

        mvc.perform(put("/api/event-templates/" + therapie.id()).with(as(mike)).contentType(APPLICATION_JSON)
                .content(zahnarzt("Übernommen", false))).andExpect(status().isNotFound());
        mvc.perform(delete("/api/event-templates/" + therapie.id()).with(as(mike))).andExpect(status().isNotFound());
        mvc.perform(delete("/api/event-templates/" + therapie.id()).with(as(sarah)))
                .andExpect(status().isNoContent());
    }
}
