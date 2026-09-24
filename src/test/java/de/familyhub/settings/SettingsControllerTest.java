package de.familyhub.settings;

import static de.familyhub.testsupport.TestUsers.as;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.empty;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;

import de.familyhub.calendar.CalendarEventRepository;
import de.familyhub.family.FamilyMember;
import de.familyhub.family.FamilyMemberRepository;
import de.familyhub.permission.Role;
import de.familyhub.testsupport.TestUsers;

@SpringBootTest
@AutoConfigureMockMvc
class SettingsControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private FamilyMemberRepository members;

    @Autowired
    private CalendarEventRepository events;

    @Autowired
    private FamilySettingsRepository settings;

    private FamilyMember sarah;
    private FamilyMember emma;

    @BeforeEach
    void setUp() {
        events.deleteAll();
        members.deleteAll();
        settings.deleteAll();
        sarah = members.save(TestUsers.member("Sarah", Role.ADMINISTRATOR));
        emma = members.save(TestUsers.member("Emma", Role.JUGENDLICHER));
    }

    @Test
    void guestsSeeNoCategoriesUntilAnAdministratorReleasesThem() throws Exception {
        mvc.perform(get("/api/settings").with(as(emma)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.guestCategories", empty()));
    }

    @Test
    void administratorReleasesCategoriesForGuests() throws Exception {
        mvc.perform(put("/api/settings").with(as(sarah)).contentType(APPLICATION_JSON)
                        .content("{\"guestCategories\": [\"family\", \"school\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.guestCategories", containsInAnyOrder("family", "school")));

        mvc.perform(get("/api/settings").with(as(emma)))
                .andExpect(jsonPath("$.guestCategories", containsInAnyOrder("family", "school")));
    }

    @Test
    void onlyAdministratorsMayChangeSettings() throws Exception {
        mvc.perform(put("/api/settings").with(as(emma)).contentType(APPLICATION_JSON)
                        .content("{\"guestCategories\": [\"family\"]}"))
                .andExpect(status().isForbidden());
    }
}
