package de.familyhub.ors;


import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.StringUtils;

// Einstellungen für OpenRouteService. Der API-Schlüssel steht nie im Git, sondern in local.properties (siehe README).
// Fehlt er, ist ORS "nicht eingerichtet".
@ConfigurationProperties("familyhub.ors")
public record OrsProperties(
        String apiKey,
        @DefaultValue("https://api.openrouteservice.org") String baseUrl) {
    public boolean isConfigured() {
        return StringUtils.hasText(apiKey);
    }
}
