package de.familyhub.whatsapp;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import io.swagger.v3.oas.annotations.media.Schema;

// Eine eingehende WhatsApp-Nachricht, roh und unverändert gespeichert: raw enthält den genauen
// JSON-Payload der Nachricht, wie er vom Webhook gekommen ist. from/text/timestamp sind nur Zugriffs-
// bequemlichkeiten. Auswertung, Analyse und Antworten gehören (noch nicht) dazu, sondern zu einem späteren
// KI-Service, der auf diese Rohdaten zugreift.
@Document("whatsappMessages")
@CompoundIndex(name = "receivedAt", def = "{'receivedAt': 1}")
public record WhatsAppMessage(
        @Id
        @Schema(description = "WhatsApp-Nachrichten-ID (wamid…); dient zur Duplikatvermeidung bei erneuter Zustellung") String id,
        @Schema(description = "Absender-Nummer oder Gruppen-ID, wie sie im Payload steht") String from,
        @Schema(description = "Textkörper, sofern es sich um eine Textnachricht handelt (sonst null)") String text,
        @Schema(description = "Zeitstempel laut WhatsApp (Epochensekunden im Payload)") Instant timestamp,
        @Schema(description = "Wann FamilyHub den Webhook empfangen hat") Instant receivedAt,
        @Schema(description = "Roh-Payload der Nachricht (JSON), unverändert wie empfangen") String raw) {
}
