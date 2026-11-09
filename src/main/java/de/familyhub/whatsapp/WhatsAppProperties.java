package de.familyhub.whatsapp;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.StringUtils;

// Einstellungen für die WhatsApp Cloud API (Meta). Die Zugangsdaten stehen nie im Git: application.properties liest
// sie aus den Umgebungsvariablen WHATSAPP_ACCESS_TOKEN, WHATSAPP_PHONE_NUMBER_ID, WHATSAPP_VERIFY_TOKEN und
// WHATSAPP_APP_SECRET; ein Wert in local.properties (familyhub.whatsapp.*, siehe README) gewinnt. Fehlt einer, ist
// die Anbindung "nicht eingerichtet".
@ConfigurationProperties("familyhub.whatsapp")
public record WhatsAppProperties(
        String accessToken,
        String phoneNumberId,
        // Frei gewählter Wert, den Meta beim Einrichten des Webhooks mitschickt
        String verifyToken,
        // App-Geheimnis aus dem Meta-Portal; damit signiert Meta jeden Aufruf des Webhooks (WhatsAppSignature)
        String appSecret,
        @DefaultValue("https://graph.facebook.com/v21.0") String apiBaseUrl) {

    public boolean isConfigured() {
        return StringUtils.hasText(accessToken) && StringUtils.hasText(phoneNumberId)
                && StringUtils.hasText(verifyToken) && StringUtils.hasText(appSecret);
    }
}
