package de.familyhub.ors;

import static de.familyhub.testsupport.TestUsers.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import de.familyhub.calendar.EventCategory;
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

    private static String locationJson(String label, double lat, double lon) {
        return "{\"label\":"+label+", \"lat\": " + lat
                + ", \"lon\": " + lon + "}";
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
            .andExpect(jsonPath("$[1].lon").value(30.0));}

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

}