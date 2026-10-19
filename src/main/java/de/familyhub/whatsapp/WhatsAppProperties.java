package de.familyhub.whatsapp;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.StringUtils;

// Einstellungen für die WhatsApp Cloud API (Meta). Die Zugangsdaten stehen nie im Git, sondern als
// Umgebungsvariablen WHATSAPP_ACCESS_TOKEN, WHATSAPP_PHONE_NUMBER_ID und WHATSAPP_VERIFY_TOKEN oder in
// local.properties (familyhub.whatsapp.*, siehe README). Wer in local.properties einen Wert setzt, gewinnt
// vor der Umgebungsvariablen. Fehlt der Verify-Token, ist der Webhook "nicht eingerichtet".
@ConfigurationProperties("familyhub.whatsapp")
public record WhatsAppProperties(
        @DefaultValue("${WHATSAPP_ACCESS_TOKEN:}") String accessToken,
        @DefaultValue("${WHATSAPP_PHONE_NUMBER_ID:}") String phoneNumberId,
        @DefaultValue("${WHATSAPP_VERIFY_TOKEN:}") String verifyToken,
        @DefaultValue("https://graph.facebook.com/v21.0") String apiBaseUrl) {

    public boolean isConfigured() {
        return StringUtils.hasText(accessToken) && StringUtils.hasText(phoneNumberId)
                && StringUtils.hasText(verifyToken);
    }
}
