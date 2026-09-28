package de.familyhub.weather;

import java.time.Clock;
import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;

import de.familyhub.web.ApiException;

// Wetter für den Wohnort der Familie. Ein Abruf gilt familyhub.weather.cache-duration lang (Standard 10 Minuten),
// damit nicht jede geöffnete Übersicht OpenWeather fragt. Fehler werden nicht zwischengespeichert.
@Service
public class WeatherService {

    private record Cached(WeatherLocation location, WeatherReport report, Instant fetchedAt) {
    }

    private final OpenWeatherApi api;
    private final WeatherProperties properties;
    private final Clock clock;
    private volatile Cached cached;

    public WeatherService(OpenWeatherApi api, WeatherProperties properties, Clock clock) {
        this.api = api;
        this.properties = properties;
        this.clock = clock;
    }

    public WeatherReport report(WeatherLocation location) {
        requireConfigured();
        Instant now = clock.instant();
        Cached hit = cached;
        if (hit != null && hit.location().equals(location)
                && hit.fetchedAt().plus(properties.cacheDuration()).isAfter(now)) {
            return hit.report();
        }
        try {
            WeatherReport report = WeatherReports.build(location.name(), api.current(location.lat(), location.lon()),
                    api.forecast(location.lat(), location.lon()));
            cached = new Cached(location, report, now);
            return report;
        } catch (WeatherException e) {
            throw ApiException.unavailable(e.getMessage());
        }
    }

    public List<WeatherLocation> findPlaces(String query) {
        requireConfigured();
        try {
            return api.findPlaces(query).stream()
                    .map(p -> new WeatherLocation(p.germanName(), p.state(), p.country(), p.lat(), p.lon()))
                    .toList();
        } catch (WeatherException e) {
            throw ApiException.unavailable(e.getMessage());
        }
    }

    private void requireConfigured() {
        if (!properties.isConfigured()) {
            throw ApiException.unavailable("Das Wetter ist nicht eingerichtet: Es fehlt der OpenWeather-API-Schlüssel "
                    + "(familyhub.weather.api-key in local.properties, siehe README).");
        }
    }
}
