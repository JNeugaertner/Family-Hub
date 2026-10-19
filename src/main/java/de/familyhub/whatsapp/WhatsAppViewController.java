package de.familyhub.whatsapp;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.familyhub.security.CurrentMember;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

// Lesezugriff auf die empfangenen WhatsApp-Nachrichten und den Anbindungsstatus für die Oberfläche.
// Anders als der öffentliche Webhook (WhatsAppWebhookController) liegt das hier hinter der Anmeldung.
@RestController
@RequestMapping("/api/whatsapp")
@Tag(name = "WhatsApp")
public class WhatsAppViewController {

    private final WhatsAppProperties properties;
    private final WhatsAppService service;
    private final CurrentMember currentMember;

    public WhatsAppViewController(WhatsAppProperties properties, WhatsAppService service, CurrentMember currentMember) {
        this.properties = properties;
        this.service = service;
        this.currentMember = currentMember;
    }

    public record Status(boolean configured, long messageCount) {
    }

    @GetMapping("/status")
    @Operation(summary = "Status der WhatsApp-Anbindung",
            description = "configured zeigt, ob Zugangsdaten gesetzt sind; messageCount, wie viele Nachrichten bereits "
                    + "empfangen und gespeichert wurden.")
    public Status status() {
        currentMember.get();
        return new Status(properties.isConfigured(), service.count());
    }

    @GetMapping("/messages")
    @Operation(summary = "Empfangene WhatsApp-Nachrichten",
            description = "Die letzten 100 empfangenen Nachrichten, neueste zuerst, inkl. des rohen Payloads.")
    public List<WhatsAppMessage> messages() {
        currentMember.get();
        return service.recent();
    }
}
