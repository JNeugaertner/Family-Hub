package de.familyhub.ors;

import static de.familyhub.testsupport.TestUsers.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import de.familyhub.family.FamilyMember;
import de.familyhub.family.FamilyMemberRepository;
import de.familyhub.permission.Role;
import de.familyhub.settings.FamilySettings;
import de.familyhub.settings.FamilySettingsRepository;
import de.familyhub.testsupport.TestUsers;
import de.familyhub.ors.OrsApi.SearchResponse;
import de.familyhub.ors.OrsApi.Feature;
import de.familyhub.ors.OrsApi.Geometry;
import de.familyhub.ors.OrsApi.FeatureProperties;
import de.familyhub.ors.OrsApi.RouteResponse;
import de.familyhub.ors.OrsApi.RouteFeature;
import de.familyhub.ors.OrsApi.RouteProperties;
import de.familyhub.ors.OrsApi.RouteSummary;

@SpringBootTest
@AutoConfigureMockMvc
class OrsControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private FamilyMemberRepository members;

    @Autowired
    private FamilySettingsRepository settings;

    @MockitoBean
    private OrsApi api;

    private FamilyMember sarah;
    private FamilyMember emma;
    private FamilyMember lucas;
    private FamilyMember oma;

    @BeforeEach
    void setUp() {
        members.deleteAll();
        settings.deleteAll();
        sarah = members.save(TestUsers.member("Sarah", Role.ADMINISTRATOR));
        emma = members.save(TestUsers.member("Emma", Role.JUGENDLICHER));
        lucas = members.save(TestUsers.member("Lucas", Role.KIND));
        oma = members.save(TestUsers.member("Oma", Role.GAST));
    }

    private void simulatePlaces() {
        when(api.findPlaces(anyString())).thenReturn(new SearchResponse(List.of(
                new Feature(new Geometry(List.of(10.0, 20.0)), new FeatureProperties("Place A")),
                new Feature(new Geometry(List.of(30.0, 40.0)), new FeatureProperties("Place B"))
        )));
    }

    private void simulateRoute() {
        when(api.gettravelTime(anyString(), anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenReturn(new RouteResponse(List.of(
                        new RouteFeature(new RouteProperties(new RouteSummary(12,400)))
                )));
    }

    @Test
    void userCanSearchPlaces() throws Exception {
        simulatePlaces();

        mvc.perform(get("/api/ors")
                .param("q", "place")
                .with(as(sarah)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].label").value("Place A"))
            .andExpect(jsonPath("$[0].lat").value(20.0))
            .andExpect(jsonPath("$[0].lon").value(10.0))
            .andExpect(jsonPath("$[1].label").value("Place B"))
            .andExpect(jsonPath("$[1].lat").value(40.0))
            .andExpect(jsonPath("$[1].lon").value(30.0));
        }

    @Test
    void userCanGetRoute() throws Exception {
        simulateRoute();

        mvc.perform(get("/api/ors/travelTime")
                .param("transportMode", "driving-car")
                .param("startLat", "20.0")
                .param("startLon", "10.0")
                .param("endLat", "40.0")
                .param("endLon", "30.0")
                .with(as(sarah)))
            .andExpect(status().isOk())
            .andExpect(content().string("400.0"));}

    @Test
    void blankSearchIsRejected() throws Exception {
        mvc.perform(get("/api/ors")
                .param("q", " ")
                .with(as(sarah)))
                .andDo(print())
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.q").value("Bitte einen Ort eingeben."));}

    @Test 
    void searchErrorBecomesBadRequest() throws Exception {
        when(api.findPlaces(anyString())).thenThrow(new OrsException("ungültige Anfrage"));

        mvc.perform(get("/api/ors")
                .param("q", "place")
                .with(as(sarah)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.location").value("ungültige Anfrage"));}

    @Test 
    void routeErrorBecomesBadRequest() throws Exception {
        when(api.gettravelTime(anyString(), anyDouble(), anyDouble(), anyDouble(), anyDouble()))
                .thenThrow(new OrsException("ungültige Anfrage"));

        mvc.perform(get("/api/ors/travelTime")
                .param("transportMode", "driving-car")
                .param("startLat", "20.0")
                .param("startLon", "10.0")
                .param("endLat", "40.0")
                .param("endLon", "30.0")
                .with(as(sarah)))
                .andDo(print())
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.errors.travelTime").value("ungültige Anfrage"));}

    @Test 
    void adminMaySetLocationOthersMayNot() throws Exception {
        mvc.perform(put("/api/ors/location")
                .with(as(emma))
                .contentType(APPLICATION_JSON)
                .content("{\"label\":\"Place A\",\"lat\":20.0,\"lon\":10.0}"))
                .andDo(print())
            .andExpect(status().isForbidden());

        mvc.perform(put("/api/ors/location")
                .with(as(lucas))
                .contentType(APPLICATION_JSON)
                .content("{\"label\":\"Place A\",\"lat\":20.0,\"lon\":10.0}"))
            .andExpect(status().isForbidden());

        mvc.perform(put("/api/ors/location")
                .with(as(oma))
                .contentType(APPLICATION_JSON)
                .content("{\"label\":\"Place A\",\"lat\":20.0,\"lon\":10.0}"))
            .andExpect(status().isForbidden());

        mvc.perform(put("/api/ors/location")
                .with(as(sarah))
                .contentType(APPLICATION_JSON)
                .content("{\"label\":\"Place A\",\"lat\":20.0,\"lon\":10.0}"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.label").value("Place A"))
            .andExpect(jsonPath("$.lat").value(20.0))
            .andExpect(jsonPath("$.lon").value(10.0));

        assertThat(settings.current().homeLocation()).isNotNull();
        assertThat(settings.current().homeLocation().label()).isEqualTo("Place A");
        assertThat(settings.current().homeLocation().lat()).isEqualTo(20.0);
        assertThat(settings.current().homeLocation().lon()).isEqualTo(10.0);
    }

    @Test 
    void adminMayRemoveLocationOthersMayNot() throws Exception {
        settings.save(new FamilySettings(FamilySettings.ID, Set.of())
                .withHomeLocation(new OrsLocation("Place A", 20.0, 10.0)));

        mvc.perform(delete("/api/ors/location")
                .with(as(emma)))
            .andExpect(status().isForbidden());

        mvc.perform(delete("/api/ors/location")
                .with(as(lucas)))
            .andExpect(status().isForbidden());

        mvc.perform(delete("/api/ors/location")
                .with(as(oma)))
            .andExpect(status().isForbidden());

        mvc.perform(delete("/api/ors/location")
                .with(as(sarah)))
            .andExpect(status().isNoContent());

        assertThat(settings.current().homeLocation()).isNull();
    }

}