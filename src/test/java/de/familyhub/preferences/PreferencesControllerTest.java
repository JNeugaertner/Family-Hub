package de.familyhub.preferences;

import static de.familyhub.testsupport.TestUsers.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
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

import de.familyhub.family.FamilyMember;
import de.familyhub.family.FamilyMemberRepository;
import de.familyhub.permission.Role;
import de.familyhub.testsupport.TestUsers;

@SpringBootTest
@AutoConfigureMockMvc
class PreferencesControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private FamilyMemberRepository members;

    @Autowired
    private MemberPreferencesRepository preferences;

    private FamilyMember sarah;
    private FamilyMember emma;
    private FamilyMember oma;

    @BeforeEach
    void setUp() {
        preferences.deleteAll();
        members.deleteAll();
        sarah = members.save(TestUsers.member("Sarah", Role.ADMINISTRATOR));
        emma = members.save(TestUsers.member("Emma", Role.KIND));
        oma = members.save(TestUsers.member("Oma", Role.GAST));
    }

    private void save(FamilyMember who, boolean countdown, boolean calendar) throws Exception {
        mvc.perform(put("/api/preferences").with(as(who)).contentType(APPLICATION_JSON).content("""
                        {"holidayCountdown": %s, "holidaysInCalendar": %s}
                        """.formatted(countdown, calendar)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.holidayCountdown").value(countdown))
                .andExpect(jsonPath("$.holidaysInCalendar").value(calendar));
    }

    @Test
    void everythingIsOnUntilSomeoneChangesIt() throws Exception {
        mvc.perform(get("/api/preferences").with(as(oma)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.holidayCountdown").value(true))
                .andExpect(jsonPath("$.holidaysInCalendar").value(true));
        assertThat(preferences.count()).isZero();
    }

    @Test
    void eachPersonDecidesForThemselves() throws Exception {
        save(emma, false, true);
        save(oma, true, false);

        mvc.perform(get("/api/preferences").with(as(emma)))
                .andExpect(jsonPath("$.holidayCountdown").value(false))
                .andExpect(jsonPath("$.holidaysInCalendar").value(true));
        mvc.perform(get("/api/preferences").with(as(oma)))
                .andExpect(jsonPath("$.holidayCountdown").value(true))
                .andExpect(jsonPath("$.holidaysInCalendar").value(false));
        mvc.perform(get("/api/preferences").with(as(sarah)))
                .andExpect(jsonPath("$.holidayCountdown").value(true))
                .andExpect(jsonPath("$.holidaysInCalendar").value(true));

        save(emma, true, true);
        mvc.perform(get("/api/preferences").with(as(emma)))
                .andExpect(jsonPath("$.holidayCountdown").value(true));
    }

    @Test
    void resetTurnsEverythingBackOn() throws Exception {
        save(emma, false, false);
        mvc.perform(delete("/api/preferences").with(as(emma))).andExpect(status().isNoContent());
        mvc.perform(get("/api/preferences").with(as(emma)))
                .andExpect(jsonPath("$.holidayCountdown").value(true))
                .andExpect(jsonPath("$.holidaysInCalendar").value(true));
        assertThat(preferences.count()).isZero();
    }

    @Test
    void bothValuesAreRequired() throws Exception {
        mvc.perform(put("/api/preferences").with(as(emma)).contentType(APPLICATION_JSON)
                        .content("{\"holidayCountdown\": false}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.holidaysInCalendar").exists());
    }

    @Test
    void deletingAMemberDeletesTheirPreferences() throws Exception {
        save(oma, false, false);
        mvc.perform(delete("/api/members/" + oma.id()).with(as(sarah))).andExpect(status().isNoContent());
        assertThat(preferences.findById(oma.id())).isEmpty();
    }

    @Test
    void needsLogin() throws Exception {
        mvc.perform(get("/api/preferences")).andExpect(status().isUnauthorized());
    }
}
