package de.familyhub.whatsapp;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

// Webhook der Meta WhatsApp Cloud API. Meta prüft die Adresse einmalig mit einer GET-Anfrage
// (Hub-Verifizierung) und liefert danach eingehende Nachrichten per POST. Beides darf ohne Anmeldung und ohne
// CSRF-Schutz erreichbar sein (siehe SecurityConfig); dafür muss jeder POST die Signatur von Meta tragen
// (WhatsAppSignature). Der Controller speichert eingehende Nachrichten nur roh ab und antwortet nie automatisch:
// Das Auswerten und Beantworten übernimmt später ein KI-Service, der auf WhatsAppService/WhatsAppMessage aufsetzt.
@RestController
@RequestMapping("/api/whatsapp")
@Tag(name = "WhatsApp")
public class WhatsAppWebhookController {

    private static final Logger LOG = LoggerFactory.getLogger(WhatsAppWebhookController.class);

    private static final String VERIFY_MODE = "verify";

    private final WhatsAppProperties properties;
    private final WhatsAppService service;
    private final WhatsAppSignature signature;
    private final JsonMapper jsonMapper;

    public WhatsAppWebhookController(WhatsAppProperties properties, WhatsAppService service,
            WhatsAppSignature signature, JsonMapper jsonMapper) {
        this.properties = properties;
        this.service = service;
        this.signature = signature;
        this.jsonMapper = jsonMapper;
    }

    /**
     * Hub-Verifizierung von Meta: Meta ruft die Webhook-Adresse mit den Parametern hub.challenge und
     * hub.verify_token auf und erwartet den Challenge-Wert unverändert als Antwort. Stimmt der Token nicht oder ist
     * keiner eingerichtet, wird abgelehnt, damit nur die konfigurierte WhatsApp-Nummer auf den Webhook zeigen darf.
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
        if (!StringUtils.hasText(properties.verifyToken()) || !properties.verifyToken().equals(verifyToken)
                || challenge == null) {
            return ResponseEntity.status(HttpStatus.FORBIDDEN).body("Nicht autorisiert");
        }
        return ResponseEntity.ok(challenge);
    }

    /**
     * Eingehende Nachrichten von Meta: Erst die Signatur über den unveränderten Body prüfen, dann den Payload
     * zerlegen und jede Nachricht roh in der Datenbank speichern. Meta erwartet für die Zustellung einen 2xx; der
     * Body bleibt leer.
     */
    @PostMapping
    @Operation(summary = "Eingehende WhatsApp-Nachrichten entgegennehmen (nur für Meta)",
            description = "Nur mit gültiger Signatur von Meta (X-Hub-Signature-256), sonst 401. Speichert jede "
                    + "Nachricht roh ab und antwortet leer. Kein automatischer Rücklauf.")
    public ResponseEntity<Void> incoming(@RequestBody(required = false) byte[] body,
            @RequestHeader(value = WhatsAppSignature.HEADER, required = false) String signatureHeader) {
        if (!StringUtils.hasText(properties.appSecret())) {
            LOG.warn("WhatsApp-Webhook abgelehnt: Das App-Secret ist nicht eingerichtet (siehe README)");
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build();
        }
        if (!signature.isValid(body, signatureHeader)) {
            LOG.warn("WhatsApp-Webhook mit fehlender oder falscher Signatur abgelehnt");
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        JsonNode payload;
        try {
            payload = jsonMapper.readTree(body);
        } catch (JacksonException e) {
            LOG.warn("WhatsApp-Webhook mit ungültigem JSON abgelehnt: {}", e.getOriginalMessage());
            return ResponseEntity.badRequest().build();
        }
        if (payload == null || payload.isMissingNode()) {
            return ResponseEntity.badRequest().build();
        }
        service.storeIncoming(payload);
        return ResponseEntity.ok().build();
    }
}
