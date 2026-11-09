package de.familyhub.inbox;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

import io.swagger.v3.oas.annotations.media.Schema;

// Eine Nachricht, die über einen externen Kanal (WhatsApp, Telegram) hereinkam, roh und unverändert gespeichert
// (Entscheidung vom 21.10.2026: ein gemeinsamer Eingang für alle Kanäle). Wer sie sieht, regelt InboxAccess über
// senderKey. Auswertung und Antworten gehören nicht hierher, sondern zu einem späteren KI-Service, der auf diese
// Rohdaten zugreift.
@Document("inboundMessages")
@CompoundIndex(name = "senderKeyReceivedAt", def = "{'senderKey': 1, 'receivedAt': -1}")
public record InboundMessage(
        @Id
        @Schema(description = "Kanal und Nachrichten-ID im Kanal, z. B. whatsapp:wamid.…; verhindert doppeltes Speichern")
        String id,
        Channel channel,
        @Schema(description = "Absender im Kanal: bei WhatsApp die Handynummer, bei Telegram die Nutzer-ID")
        String sender,
        @Schema(description = "Kanal und Absender, z. B. whatsapp:4917012345678")
        String senderKey,
        @Schema(description = "Anzeigename, sofern der Kanal ihn mitliefert (Telegram)")
        String senderName,
        @Schema(description = "Textkörper; null bei Bildern, Sprachnachrichten und anderem")
        String text,
        @Schema(description = "Wann die Nachricht laut Kanal geschrieben wurde")
        Instant sentAt,
        @Schema(description = "Wann FamilyHub sie empfangen hat")
        Instant receivedAt,
        @Schema(description = "Rohdaten der Nachricht (JSON), unverändert wie empfangen")
        String raw) {

    public static InboundMessage of(Channel channel, String messageId, String sender, String senderName, String text,
            Instant sentAt, Instant receivedAt, String raw) {
        return new InboundMessage(channel.prefixed(messageId), channel, sender, channel.prefixed(sender), senderName,
                text, sentAt, receivedAt, raw);
    }
}
