package de.familyhub.holidays;

import static de.familyhub.testsupport.TestUsers.as;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
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
class HolidayControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private FamilyMemberRepository members;

    private FamilyMember guest;

    @BeforeEach
    void setUp() {
        members.deleteAll();
        guest = members.save(TestUsers.member("Oma", Role.GAST));
    }

    @Test
    void evenGuestsSeeTheHolidaysOfARange() throws Exception {
        mvc.perform(get("/api/holidays").param("from", "2027-03-01").param("to", "2027-05-31").with(as(guest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].key").value("ostern"))
                .andExpect(jsonPath("$[0].name").value("Ostern"))
                .andExpect(jsonPath("$[0].date").value("2027-03-28"))
                .andExpect(jsonPath("$[1].date").value("2027-05-06"))
                .andExpect(jsonPath("$[2].date").value("2027-05-16"));
    }

    @Test
    void upcomingDefaultsToThreeSorted() throws Exception {
        mvc.perform(get("/api/holidays/upcoming").with(as(guest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].daysLeft").isNumber());
    }

    @Test
    void rejectsBadRangesAndCounts() throws Exception {
        mvc.perform(get("/api/holidays").param("from", "2027-05-01").param("to", "2027-04-01").with(as(guest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.to").value("Das Ende darf nicht vor dem Anfang liegen."));
        mvc.perform(get("/api/holidays").param("from", "2027-01-01").param("to", "2029-01-01").with(as(guest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.to").value("Höchstens 400 Tage auf einmal."));
        mvc.perform(get("/api/holidays/upcoming").param("count", "0").with(as(guest)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/holidays/upcoming").param("count", "7").with(as(guest)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void needsLogin() throws Exception {
        mvc.perform(get("/api/holidays/upcoming")).andExpect(status().isUnauthorized());
    }
}
