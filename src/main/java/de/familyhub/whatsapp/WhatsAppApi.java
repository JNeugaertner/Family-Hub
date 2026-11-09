package de.familyhub.whatsapp;

import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

// Aufrufe an die offizielle Meta WhatsApp Cloud API (Graph API). Aktuell braucht FamilyHub nur die
// Grundbausteine, damit die Anbindung später auch Nachrichten senden kann; das eigentliche Antworten
// überlässt ein späterer KI-Service diesem Baustein. Der Basis-URL-Punkt (z. B. v21.0) ist konfigurierbar.
@Component
public class WhatsAppApi {

    private final WhatsAppProperties properties;
    private final RestClient rest;

    @Autowired
    public WhatsAppApi(WhatsAppProperties properties) {
        this(properties, RestClient.builder());
    }

    // Für Tests, die Meta mit MockRestServiceServer simulieren
    WhatsAppApi(WhatsAppProperties properties, RestClient.Builder builder) {
        this.properties = properties;
        this.rest = builder.build();
    }

    // Antwort der Cloud API auf ein Senden: darin liegt eine Liste mit der empfangenen Nachrichten-ID (wamid…)
    public record SendResponse(List<MessageRef> messages) {
        public record MessageRef(String id) {
        }
    }

    /**
     * Sendet eine kurze Textnachricht an eine Telefonnummer (mit Ländervorwahl, ohne +).
     * Liefert die empfangene Nachrichten-ID zurück.
     */
    public String sendText(String toPhone, String body) {
        if (!properties.isConfigured()) {
            throw new WhatsAppException("Die WhatsApp-Anbindung ist nicht eingerichtet: Es fehlen Zugangsdaten "
                    + "(WHATSAPP_ACCESS_TOKEN / WHATSAPP_PHONE_NUMBER_ID, siehe README).");
        }
        record Payload(String messaging_product, String to, String type, TextText text) {
            record TextText(String body) {
            }
        }
        String url = properties.apiBaseUrl() + "/" + properties.phoneNumberId() + "/messages";
        // Das Payload-Mapping ist bewusst minimal; später ergänzt ein KI-Service die Nachrichtentypen.
        SendResponse response = rest.post().uri(url)
                .header("Authorization", "Bearer " + properties.accessToken())
                .body(new Payload("whatsapp", toPhone, "text", new Payload.TextText(body)))
                .retrieve()
                .body(SendResponse.class);
        return response == null || response.messages() == null || response.messages().isEmpty()
                ? null
                : response.messages().get(0).id();
    }
}
