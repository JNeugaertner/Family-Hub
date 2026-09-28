package de.familyhub.google;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.StringUtils;

// Einstellungen für die Google-Kalender-Anbindung. Client-ID, Client-Secret und Token-Schlüssel stehen nie im Git,
// sondern in local.properties (siehe README). Fehlen sie, ist die Anbindung "nicht eingerichtet".
@ConfigurationProperties("familyhub.google")
public record GoogleProperties(
        String clientId,
        String clientSecret,
        // Schlüssel, mit dem die Refresh-Tokens in der Datenbank verschlüsselt werden
        String tokenKey,
        @DefaultValue("http://localhost:8080/api/google/callback") String redirectUri,
        // Wohin der Browser nach der Anmeldung bei Google zurückkehrt
        @DefaultValue("http://localhost:5173") String frontendUrl,
        @DefaultValue("PT15M") Duration syncInterval,
        @DefaultValue("https://accounts.google.com/o/oauth2/v2/auth") String authUrl,
        @DefaultValue("https://oauth2.googleapis.com/token") String tokenUrl,
        @DefaultValue("https://oauth2.googleapis.com/revoke") String revokeUrl,
        @DefaultValue("https://www.googleapis.com/calendar/v3") String apiUrl) {

    public boolean isConfigured() {
        return StringUtils.hasText(clientId) && StringUtils.hasText(clientSecret) && StringUtils.hasText(tokenKey);
    }
}
