package de.familyhub.whatsapp;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import de.familyhub.inbox.Channel;
import de.familyhub.inbox.InboundMessage;
import de.familyhub.inbox.Inbox;
import tools.jackson.databind.JsonNode;

// Nimmt eingehende WhatsApp-Nachrichten entgegen und legt sie roh im gemeinsamen Eingang ab (Inbox): Der
// JSON-Payload wird unverändert gespeichert, Absender, Text und Zeit zusätzlich ausgelesen, damit sie sich leicht
// anzeigen und filtern lassen. Hier findet keine Auswertung statt, weder Analyse noch automatische Antworten.
@Service
public class WhatsAppService {

    private static final Logger LOG = LoggerFactory.getLogger(WhatsAppService.class);

    private final Inbox inbox;
    private final Clock clock;

    public WhatsAppService(Inbox inbox, Clock clock) {
        this.inbox = inbox;
        this.clock = clock;
    }

    /**
     * Zerlegt einen eingehenden WhatsApp-Payload in seine Einzelnachrichten und speichert jede roh ab.
     * Meta kann dieselbe Nachricht erneut zustellen (z. B. ohne Bestätigung nach Zeitüberschreitung); anhand
     * der Nachrichten-ID wird ein zweiter Lauf übersprungen, damit nichts doppelt gespeichert wird.
     *
     * @return die neu gespeicherten Nachrichten (leer, falls alles bereits bekannt war)
     */
    public List<InboundMessage> storeIncoming(JsonNode payload) {
        if (payload == null) {
            throw new WhatsAppException("Leerer Webhook-Payload");
        }
        JsonNode entries = payload.path("entry");
        if (entries.isEmpty()) {
            LOG.debug("WhatsApp-Webhook ohne Einträge ignoriert");
            return List.of();
        }

        List<InboundMessage> stored = new ArrayList<>();
        for (JsonNode entry : entries) {
            for (JsonNode change : entry.path("changes")) {
                for (JsonNode value : change.path("value").path("messages")) {
                    InboundMessage message = storeOne(value);
                    if (message != null) {
                        stored.add(message);
                    }
                }
            }
        }
        return stored;
    }

    // Zerlegt eine einzelne Nachricht und legt sie roh ab; null, wenn sie bereits gespeichert war
    private InboundMessage storeOne(JsonNode message) {
        String id = textOrNull(message, "id");
        if (id == null) {
            LOG.warn("WhatsApp-Nachricht ohne ID ignoriert");
            return null;
        }
        String from = textOrNull(message, "from");
        // Der Textkörper steckt je nach Nachrichtentyp in text.body; bei anderen Typen gibt es keinen Text
        String text = "text".equals(textOrNull(message, "type")) ? textOrNull(message.path("text"), "body") : null;
        long seconds = message.path("timestamp").asLong(0);
        Instant sentAt = seconds > 0 ? Instant.ofEpochSecond(seconds) : null;

        InboundMessage inbound = InboundMessage.of(Channel.WHATSAPP, id, from, null, text, sentAt, clock.instant(),
                message.toString());
        if (!inbox.store(inbound)) {
            LOG.info("WhatsApp-Nachricht {} kommt erneut und wird übersprungen", id);
            return null;
        }
        LOG.info("Eingehende WhatsApp-Nachricht {} von {} gespeichert", id, from);
        return inbound;
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isString() ? value.stringValue() : null;
    }
}
