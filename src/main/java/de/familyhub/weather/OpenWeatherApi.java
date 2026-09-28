package de.familyhub.weather;

import static java.nio.charset.StandardCharsets.UTF_8;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import com.fasterxml.jackson.annotation.JsonProperty;

// Aufrufe an OpenWeather mit den Schnittstellen, die jeder (auch kostenlose) Schlüssel nutzen darf:
// aktuelles Wetter, Vorhersage in 3-Stunden-Schritten für 5 Tage und die Ortssuche.
@Component
public class OpenWeatherApi {

    private final WeatherProperties properties;
    private final RestClient rest;

    @Autowired
    public OpenWeatherApi(WeatherProperties properties) {
        this(properties, RestClient.builder());
    }

    // Für Tests, die OpenWeather mit MockRestServiceServer simulieren
    OpenWeatherApi(WeatherProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.rest = builder.build();
    }

    public record Condition(int id, String main, String description, String icon) {
    }

    public record Main(
            double temp,
            @JsonProperty("feels_like") double feelsLike,
            @JsonProperty("temp_min") double tempMin,
            @JsonProperty("temp_max") double tempMax,
            int humidity) {
    }

    public record Wind(double speed) {
    }

    // timezone: Abstand zur UTC-Zeit in Sekunden
    public record Current(long dt, int timezone, String name, Main main, List<Condition> weather, Wind wind) {
    }

    // pop: Regenwahrscheinlichkeit 0..1
    public record Slot(long dt, Main main, List<Condition> weather, Wind wind, Double pop) {
    }

    public record City(String name, String country, int timezone) {
    }

    public record Forecast(List<Slot> list, City city) {
    }

    public record Place(
            String name,
            @JsonProperty("local_names") Map<String, String> localNames,
            double lat,
            double lon,
            String country,
            String state) {

        // Deutscher Name, falls OpenWeather einen kennt (z. B. "München" statt "Munich")
        public String germanName() {
            return localNames != null && localNames.get("de") != null ? localNames.get("de") : name;
        }
    }

    public Current current(double lat, double lon) {
        return get("/data/2.5/weather?lat={lat}&lon={lon}&units=metric&lang=de&appid={key}",
                Map.of("lat", lat, "lon", lon, "key", properties.apiKey()), new ParameterizedTypeReference<>() {
                });
    }

    public Forecast forecast(double lat, double lon) {
        return get("/data/2.5/forecast?lat={lat}&lon={lon}&units=metric&lang=de&appid={key}",
                Map.of("lat", lat, "lon", lon, "key", properties.apiKey()), new ParameterizedTypeReference<>() {
                });
    }

    public List<Place> findPlaces(String query) {
        return get("/geo/1.0/direct?q={q}&limit=5&appid={key}", Map.of("q", query, "key", properties.apiKey()),
                new ParameterizedTypeReference<>() {
                });
    }

    private <T> T get(String path, Map<String, ?> vars, ParameterizedTypeReference<T> type) {
        try {
            return rest.get().uri(properties.baseUrl() + path, vars)
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (request, response) -> {
                        throw apiError(response);
                    })
                    .body(type);
        } catch (ResourceAccessException e) {
            throw new WeatherException("Der Wetterdienst ist gerade nicht erreichbar.");
        }
    }

    // Häufige Ursachen verständlich melden. Die Antwort enthält nie den Schlüssel, darf also ins Log.
    private static WeatherException apiError(ClientHttpResponse response) throws IOException {
        int status = response.getStatusCode().value();
        if (status == 401) {
            return new WeatherException("OpenWeather lehnt den API-Schlüssel ab. Neue Schlüssel werden erst nach "
                    + "bis zu zwei Stunden aktiv; sonst den Schlüssel in local.properties prüfen.");
        }
        if (status == 429) {
            return new WeatherException("Zu viele Anfragen an OpenWeather. Bitte später erneut versuchen.");
        }
        String detail = new String(response.getBody().readAllBytes(), UTF_8).replaceAll("\\s+", " ").strip();
        return new WeatherException("OpenWeather hat die Anfrage abgelehnt (HTTP " + status + "): "
                + (detail.length() > 200 ? detail.substring(0, 200) + "…" : detail));
    }
}
