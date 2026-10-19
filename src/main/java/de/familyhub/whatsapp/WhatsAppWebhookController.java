package de.familyhub.whatsapp;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import tools.jackson.databind.JsonNode;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

// Webhook der Meta WhatsApp Cloud API. Meta prüft die Adresse einmalig mit einer GET-Anfrage
// (Hub-Verifizierung) und liefert danach eingehende Nachrichten per POST. Beides darf ohne Anmeldung und ohne
// CSRF-Schutz erreichbar sein (siehe SecurityConfig). Der Controller speichert eingehende Nachrichten nur roh ab
// und antwortet nie automatisch: Das Auswerten und Beantworten übernimmt später ein KI-Service, der auf
// WhatsAppService/WhatsAppMessage aufsetzt.
@RestController
@RequestMapping("/api/whatsapp")
@Tag(name = "WhatsApp")
public class WhatsAppWebhookController {

    private static final String VERIFY_MODE = "verify";

    private final WhatsAppProperties properties;
    private final WhatsAppService service;

    public WhatsAppWebhookController(WhatsAppProperties properties, WhatsAppService service) {
        this.properties = properties;
        this.service = service;
    }

    /**
     * Hub-Verifizierung von Meta: Meta ruft die Webhook-Adresse mit den Parametern hub.challenge und
     * hub.verify_token auf und erwartet den Challenge-Wert unverändert als Antwort. Vergleicht der Token nicht,
     * lehnt die Antwort ab, damit nur die konfigurierte WhatsApp-Nummer auf den Webhook zeigen darf.
     */
    @GetMapping
    @Operation(summary = "Webhook-Verifizierung (nur für Meta)",
            description = "Antwortet mit dem Challenge-Wert, wenn der Verify-Token stimmt; andernfalls 403.")
    public ResponseEntity<String> verify(@RequestParam(value = "hub.mode", required = false) String mode,
            @RequestParam(value = "hub.verify_token", required = false) String verifyToken,
            @RequestParam(value = "hub.challenge", required = false) String challenge) {
        if (!VERIFY_MODE.equals(mode)) {
            return ResponseEntity.ok("OK");
        }
        if (verifyToken == null || !verifyToken.equals(properties.verifyToken()) || challenge == null) {
            return ResponseEntity.status(403).body("Nicht autorisiert");
        }
        return ResponseEntity.ok(challenge);
    }

    /**
     * Eingehende Nachrichten von Meta: Der Payload wird zerlegt und jede Nachricht roh in der Datenbank
     * gespeichert. Meta erwartet für die Zustellung einen 2xx; der Body bleibt leer.
     */
    @PostMapping
    @Operation(summary = "Eingehende WhatsApp-Nachrichten entgegennehmen",
            description = "Speichert jede Nachricht roh ab und antwortet leer. Kein automatischer Rücklauf.")
    public ResponseEntity<Void> incoming(@RequestBody(required = false) JsonNode payload) {
        service.storeIncoming(payload);
        return ResponseEntity.ok().build();
    }
}
