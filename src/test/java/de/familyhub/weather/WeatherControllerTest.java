package de.familyhub.weather;

import static de.familyhub.testsupport.TestUsers.as;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyDouble;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.http.MediaType.APPLICATION_JSON;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

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
import de.familyhub.weather.OpenWeatherApi.City;
import de.familyhub.weather.OpenWeatherApi.Condition;
import de.familyhub.weather.OpenWeatherApi.Current;
import de.familyhub.weather.OpenWeatherApi.Forecast;
import de.familyhub.weather.OpenWeatherApi.Main;
import de.familyhub.weather.OpenWeatherApi.Place;
import de.familyhub.weather.OpenWeatherApi.Wind;

// Wetter je Rolle (Entscheidungen vom 28.09.2026): Administratoren stellen den Wohnort ein, alle sehen das Wetter,
// auch Gäste. OpenWeather wird simuliert. Der Zwischenspeicher gilt je Wohnort, deshalb nutzt jeder Test einen eigenen.
@SpringBootTest
@AutoConfigureMockMvc
class WeatherControllerTest {

    @Autowired
    private MockMvc mvc;

    @Autowired
    private FamilyMemberRepository members;

    @Autowired
    private FamilySettingsRepository settings;

    @MockitoBean
    private OpenWeatherApi api;

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

    private static String locationJson(String name, double lat, double lon) {
        return "{\"name\": \"" + name + "\", \"state\": \"Baden-Württemberg\", \"country\": \"DE\", \"lat\": " + lat
                + ", \"lon\": " + lon + "}";
    }

    private void simulateWeather() {
        long now = Instant.parse("2026-09-28T07:00:00Z").getEpochSecond();
        when(api.current(anyDouble(), anyDouble())).thenReturn(new Current(now, 7200, "Stuttgart",
                new Main(17.4, 16.9, 15, 18, 64), List.of(new Condition(803, "Clouds", "überwiegend bewölkt", "04d")),
                new Wind(3)));
        when(api.forecast(anyDouble(), anyDouble())).thenReturn(new Forecast(List.of(), new City("Stuttgart", "DE",
                7200)));
    }

    @Test
    void withoutLocationTheWeatherAsksForOne() throws Exception {
        mvc.perform(get("/api/weather").with(as(sarah)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("Es ist noch kein Wohnort für das Wetter eingestellt."));
    }

    @Test
    void adminSearchesAndSetsTheLocationOthersMayNot() throws Exception {
        when(api.findPlaces("Stuttgart")).thenReturn(List.of(new Place("Stuttgart", Map.of("de", "Stuttgart"),
                48.7758, 9.1829, "DE", "Baden-Württemberg")));

        mvc.perform(get("/api/weather/places").param("q", " Stuttgart ").with(as(sarah)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].name").value("Stuttgart"))
                .andExpect(jsonPath("$[0].lat").value(48.7758));
        mvc.perform(get("/api/weather/places").param("q", "Stuttgart").with(as(emma)))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/weather/location").with(as(emma)).contentType(APPLICATION_JSON)
                        .content(locationJson("Stuttgart", 48.7758, 9.1829)))
                .andExpect(status().isForbidden());
        mvc.perform(put("/api/weather/location").with(as(sarah)).contentType(APPLICATION_JSON)
                        .content(locationJson("Stuttgart", 148, 9.1829)))
                .andExpect(status().isBadRequest());

        mvc.perform(put("/api/weather/location").with(as(sarah)).contentType(APPLICATION_JSON)
                        .content(locationJson("Stuttgart", 48.7758, 9.1829)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/settings").with(as(lucas)))
                .andExpect(jsonPath("$.weatherLocation.name").value("Stuttgart"));

        // Gäste-Kategorien ändern lässt den Wohnort stehen
        mvc.perform(put("/api/settings").with(as(sarah)).contentType(APPLICATION_JSON)
                        .content("{\"guestCategories\": [\"school\"]}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.weatherLocation.name").value("Stuttgart"));
        assertThat(settings.current().guestCategories()).containsExactly(EventCategory.SCHOOL);

        mvc.perform(delete("/api/weather/location").with(as(sarah))).andExpect(status().isNoContent());
        assertThat(settings.current().weatherLocation()).isNull();
    }

    @Test
    void everyoneIncludingGuestsSeesTheWeatherFetchedOnlyOncePerTenMinutes() throws Exception {
        settings.save(new FamilySettings(FamilySettings.ID, Set.of())
                .withWeatherLocation(new WeatherLocation("Stuttgart", null, "DE", 48.7758, 9.1829)));
        simulateWeather();

        for (FamilyMember viewer : List.of(sarah, emma, lucas, oma)) {
            mvc.perform(get("/api/weather").with(as(viewer)))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.location").value("Stuttgart"))
                    .andExpect(jsonPath("$.current.temp").value(17))
                    .andExpect(jsonPath("$.current.description").value("Überwiegend bewölkt"))
                    .andExpect(jsonPath("$.advice.kind").exists());
        }
        verify(api, times(1)).current(48.7758, 9.1829);
    }

    @Test
    void openWeatherErrorsBecomeUnavailable() throws Exception {
        settings.save(new FamilySettings(FamilySettings.ID, Set.of())
                .withWeatherLocation(new WeatherLocation("Ulm", null, "DE", 48.4, 9.99)));
        when(api.current(48.4, 9.99)).thenThrow(new WeatherException("Der Wetterdienst ist gerade nicht erreichbar."));

        mvc.perform(get("/api/weather").with(as(emma)))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.detail").value("Der Wetterdienst ist gerade nicht erreichbar."));
    }
}
