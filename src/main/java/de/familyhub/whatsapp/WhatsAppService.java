package de.familyhub.whatsapp;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import tools.jackson.databind.JsonNode;

// Nimmt eingehende WhatsApp-Nachrichten entgegen und legt sie roh ab: Der JSON-Payload wird unverändert
// in der Datenbank gespeichert, die wichtigsten Felder (Absender, Text, Zeit) zusätzlich ausgelesen,
// damit sie sich leicht anzeigen und filtern lassen. Hier findet noch keine Auswertung statt: Weder Analyse
// noch automatische Antworten. Ein späterer KI-Service greift auf diese Rohdaten zu und trennt sich so sauber
// von der WhatsApp-Empfangslogik.
@Service
public class WhatsAppService {

    private static final Logger LOG = LoggerFactory.getLogger(WhatsAppService.class);

    private final WhatsAppMessageRepository repository;

    public WhatsAppService(WhatsAppMessageRepository repository) {
        this.repository = repository;
    }

    /**
     * Zerlegt einen eingehenden WhatsApp-Payload in seine Einzelnachrichten und speichert jede roh ab.
     * Meta kann dieselbe Nachricht erneut zustellen (z. B. ohne Bestätigung nach Zeitüberschreitung); anhand
     * der Nachrichten-ID wird ein zweiter Lauf übersprungen, damit nichts doppelt gespeichert wird.
     *
     * @return die neu gespeicherten Nachrichten (leer, falls alles bereits bekannt war)
     */
    public List<WhatsAppMessage> storeIncoming(JsonNode payload) {
        if (payload == null) {
            throw new WhatsAppException("Leerer Webhook-Payload");
        }
        JsonNode entries = payload.path("entry");
        if (entries.isEmpty()) {
            LOG.debug("WhatsApp-Webhook ohne Einträge ignoriert");
            return List.of();
        }

        List<WhatsAppMessage> stored = new java.util.ArrayList<>();
        for (JsonNode entry : entries) {
            for (JsonNode change : entry.path("changes")) {
                for (JsonNode value : change.path("value").path("messages")) {
                    WhatsAppMessage message = storeOne(value);
                    if (message != null) {
                        stored.add(message);
                    }
                }
            }
        }
        return stored;
    }

    public Optional<WhatsAppMessage> findById(String id) {
        return repository.findById(id);
    }

    public List<WhatsAppMessage> recent() {
        return repository.findTop100ByOrderByReceivedAtDescIdDesc();
    }

    public long count() {
        return repository.count();
    }

    // Zerlegt eine einzelne Nachricht und legt sie roh ab; null, wenn sie bereits gespeichert war
    private WhatsAppMessage storeOne(JsonNode message) {
        String id = textOrNull(message, "id");
        if (id == null) {
            LOG.warn("WhatsApp-Nachricht ohne ID ignoriert: {}", message);
            return null;
        }
        if (repository.existsById(id)) {
            LOG.info("WhatsApp-Nachricht {} kommt erneut und wird übersprungen", id);
            return null;
        }

        String from = textOrNull(message, "from");
        // Der Textkörper steckt je nach Nachrichtentyp in text.body; bei anderen Typen gibt es keinen Text
        String text = message.path("type").asText("").equals("text") ? textOrNull(message, "text", "body") : null;
        long seconds = message.path("timestamp").asLong(0);
        Instant timestamp = seconds > 0 ? Instant.ofEpochSecond(seconds) : null;

        WhatsAppMessage stored = repository.save(new WhatsAppMessage(id, from, text, timestamp, Instant.now(),
                message.toString()));
        LOG.info("Eingehende WhatsApp-Nachricht {} von {} gespeichert", id, from);
        return stored;
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isTextual() ? value.asText() : null;
    }

    private static String textOrNull(JsonNode node, String outer, String inner) {
        return textOrNull(node.path(outer), inner);
    }
}
