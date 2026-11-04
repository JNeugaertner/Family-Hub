package de.familyhub.ors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.queryParam;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;

import java.time.Duration;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

// Die Aufrufe an OpenRouteService gegen einen simulierten Server: Parameter, Antworten und Fehler.
class OrsApiTest {

    private static final OrsProperties PROPERTIES = new OrsProperties("key-123", "https://ors.test");

    private MockRestServiceServer server;
    private OrsApi api;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        api = new OrsApi(PROPERTIES, builder);
    }

    @Test
    void findPlacesIsRequested() {
        server.expect(requestTo(startsWith("https://ors.test/geocode/search?")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("text", "Stuttgart"))
                .andExpect(header("Authorization", "key-123"))
                .andRespond(withSuccess("""
                        {"features": [{"geometry": {"coordinates": [9.1829, 48.7758]},
                                       "properties": {"label": "Stuttgart"}},{"geometry": {"coordinates": [10.0, 20.0]},
                                       "properties": {"label": "Testort"}}]}
                        """, MediaType.APPLICATION_JSON));

        List<OrsLocation> locations = api.findPlaces("Stuttgart").features().stream()
                .map(feature -> new OrsLocation(feature.properties().label(),
                        feature.geometry().coordinates().get(1),
                        feature.geometry().coordinates().get(0)))
                .toList();

        assertThat(locations).hasSize(2)
                .satisfies(l -> assertThat(l.get(0).label()).isEqualTo("Stuttgart"))
                .satisfies(l -> assertThat(l.get(1).label()).isEqualTo("Testort"))
                .satisfies(l -> assertThat(l.get(0).lat()).isEqualTo(48.7758))
                .satisfies(l -> assertThat(l.get(0).lon()).isEqualTo(9.1829))
                .satisfies(l -> assertThat(l.get(1).lat()).isEqualTo(20.0))
                .satisfies(l -> assertThat(l.get(1).lon()).isEqualTo(10.0));
        server.verify();
    }

    @Test
    void gettravelTimeIsRequested() {
        server.expect(requestTo(startsWith("https://ors.test/v2/directions/driving-car?")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("start", "9.1829,48.7758"))
                .andExpect(queryParam("end", "10.0,20.0"))
                .andExpect(header("Authorization", "key-123"))
                .andRespond(withSuccess("""
                        {"features": [{"properties": {"summary": {"distance": 77.0, "duration": 1234.5}}}]}
                        """, MediaType.APPLICATION_JSON));

        double duration = api.gettravelTime("driving-car", 48.7758, 9.1829, 20.0, 10.0).features().get(0).properties().summary().duration();

        assertThat(duration).isEqualTo(1234.5);
        server.verify();
    }

    @Test 
    void rejectedKeyGetsUnderstandableMessage() {
        server.expect(requestTo(startsWith("https://ors.test/geocode/search?")))
                .andExpect(method(HttpMethod.GET))
                .andExpect(queryParam("text", "Stuttgart"))
                .andExpect(header("Authorization", "key-123"))
                .andRespond(withStatus(HttpStatus.UNAUTHORIZED)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body("""
                                {"error": {"code": 401, "message": "ungültiger API-Key"}}
                                """));

        assertThatThrownBy(() -> api.findPlaces("Stuttgart"))
                .isInstanceOf(OrsException.class)
                .hasMessageContaining("ungültiger API-Key");
        server.verify();
    }
}