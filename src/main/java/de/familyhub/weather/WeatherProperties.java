package de.familyhub.weather;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.StringUtils;

// Einstellungen für OpenWeather. Der API-Schlüssel steht nie im Git, sondern in local.properties (siehe README).
// Fehlt er, ist das Wetter "nicht eingerichtet".
@ConfigurationProperties("familyhub.weather")
public record WeatherProperties(
        String apiKey,
        @DefaultValue("https://api.openweathermap.org") String baseUrl,
        // So lange gilt ein abgerufenes Wetter, bevor OpenWeather erneut gefragt wird
        @DefaultValue("PT10M") Duration cacheDuration) {

    public boolean isConfigured() {
        return StringUtils.hasText(apiKey);
    }
}
