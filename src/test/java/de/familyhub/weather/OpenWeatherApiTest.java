package de.familyhub.weather;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.hamcrest.Matchers.startsWith;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

// Die Aufrufe an OpenWeather gegen einen simulierten Server: Parameter, Antworten und Fehler.
class OpenWeatherApiTest {

    private static final WeatherProperties PROPERTIES = new WeatherProperties("key-123", "https://owm.test",
            Duration.ofMinutes(10));

    private MockRestServiceServer server;
    private OpenWeatherApi api;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        api = new OpenWeatherApi(PROPERTIES, builder);
    }

    @Test
    void currentWeatherIsRequestedInCelsiusAndGerman() {
        server.expect(requestTo(startsWith("https://owm.test/data/2.5/weather?")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("lat", "48.7758"))
                .andExpect(queryParam("lon", "9.1829"))
                .andExpect(queryParam("units", "metric"))
                .andExpect(queryParam("lang", "de"))
                .andExpect(queryParam("appid", "key-123"))
                .andRespond(withSuccess("""
                        {"dt": 1790665200, "timezone": 7200, "name": "Stuttgart",
                         "main": {"temp": 17.4, "feels_like": 16.9, "temp_min": 15.1, "temp_max": 18.2, "humidity": 64},
                         "weather": [{"id": 803, "main": "Clouds", "description": "überwiegend bewölkt", "icon": "04d"}],
                         "wind": {"speed": 3.6}}
                        """, MediaType.APPLICATION_JSON));

        OpenWeatherApi.Current current = api.current(48.7758, 9.1829);

        assertThat(current.main().feelsLike()).isEqualTo(16.9);
        assertThat(current.timezone()).isEqualTo(7200);
        assertThat(current.weather()).singleElement().satisfies(c -> assertThat(c.icon()).isEqualTo("04d"));
        server.verify();
    }

    @Test
    void forecastReadsSlotsWithRainChance() {
        server.expect(requestTo(startsWith("https://owm.test/data/2.5/forecast?")))
                .andExpect(queryParam("units", "metric"))
                .andRespond(withSuccess("""
                        {"list": [{"dt": 1790672400,
                                   "main": {"temp": 18, "feels_like": 17, "temp_min": 17, "temp_max": 18, "humidity": 60},
                                   "weather": [{"id": 500, "main": "Rain", "description": "leichter Regen", "icon": "10d"}],
                                   "wind": {"speed": 2.1}, "pop": 0.62}],
                         "city": {"name": "Stuttgart", "country": "DE", "timezone": 7200}}
                        """, MediaType.APPLICATION_JSON));

        OpenWeatherApi.Forecast forecast = api.forecast(48.7758, 9.1829);

        assertThat(forecast.list()).singleElement().satisfies(s -> assertThat(s.pop()).isEqualTo(0.62));
        assertThat(forecast.city().timezone()).isEqualTo(7200);
    }

    @Test
    void placeSearchPrefersGermanNames() {
        server.expect(requestTo(startsWith("https://owm.test/geo/1.0/direct?")))
                .andExpect(queryParam("q", "Munich"))
                .andExpect(queryParam("limit", "5"))
                .andRespond(withSuccess("""
                        [{"name": "Munich", "local_names": {"de": "München", "en": "Munich"},
                          "lat": 48.1371, "lon": 11.5754, "country": "DE", "state": "Bavaria"},
                         {"name": "Munich", "lat": 47.6, "lon": -101.8, "country": "US", "state": "North Dakota"}]
                        """, MediaType.APPLICATION_JSON));

        List<OpenWeatherApi.Place> places = api.findPlaces("Munich");

        assertThat(places).extracting(OpenWeatherApi.Place::germanName).containsExactly("München", "Munich");
    }

    @Test
    void rejectedKeyGetsAnUnderstandableMessage() {
        server.expect(requestTo(startsWith("https://owm.test/data/2.5/weather?")))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED).contentType(MediaType.APPLICATION_JSON)
                        .body("{\"cod\": 401, \"message\": \"Invalid API key.\"}"));

        assertThatThrownBy(() -> api.current(1, 2))
                .isInstanceOf(WeatherException.class)
                .hasMessageStartingWith("OpenWeather lehnt den API-Schlüssel ab.");
    }
}
