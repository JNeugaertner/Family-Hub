package de.familyhub.whatsapp;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.familyhub.inbox.Channel;
import de.familyhub.inbox.Inbox;
import de.familyhub.security.CurrentMember;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

// Anbindungsstatus für die Oberfläche. Anders als der öffentliche Webhook (WhatsAppWebhookController) liegt das hier
// hinter der Anmeldung. Die Nachrichten selbst liefert der gemeinsame Eingang (GET /api/inbox?channel=whatsapp).
@RestController
@RequestMapping("/api/whatsapp")
@Tag(name = "WhatsApp")
public class WhatsAppViewController {

    private final WhatsAppProperties properties;
    private final Inbox inbox;
    private final CurrentMember currentMember;

    public WhatsAppViewController(WhatsAppProperties properties, Inbox inbox, CurrentMember currentMember) {
        this.properties = properties;
        this.inbox = inbox;
        this.currentMember = currentMember;
    }

    public record Status(boolean configured, long messageCount) {
    }

    @GetMapping("/status")
    @Operation(summary = "Status der WhatsApp-Anbindung",
            description = "configured zeigt, ob Zugangsdaten gesetzt sind; messageCount, wie viele empfangene "
                    + "WhatsApp-Nachrichten die angemeldete Person sehen darf.")
    public Status status() {
        return new Status(properties.isConfigured(), inbox.countVisibleFor(currentMember.get(), Channel.WHATSAPP));
    }
}
