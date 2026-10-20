package de.familyhub.whatsapp;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.familyhub.family.FamilyMember;
import de.familyhub.security.CurrentMember;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

// Lesezugriff auf die empfangenen WhatsApp-Nachrichten und den Anbindungsstatus für die Oberfläche.
// Anders als der öffentliche Webhook (WhatsAppWebhookController) liegt das hier hinter der Anmeldung; wer welche
// Nachrichten sieht, regelt WhatsAppAccess.
@RestController
@RequestMapping("/api/whatsapp")
@Tag(name = "WhatsApp")
public class WhatsAppViewController {

    private final WhatsAppProperties properties;
    private final WhatsAppService service;
    private final WhatsAppAccess access;
    private final CurrentMember currentMember;

    public WhatsAppViewController(WhatsAppProperties properties, WhatsAppService service, WhatsAppAccess access,
            CurrentMember currentMember) {
        this.properties = properties;
        this.service = service;
        this.access = access;
        this.currentMember = currentMember;
    }

    public record Status(boolean configured, long messageCount) {
    }

    @GetMapping("/status")
    @Operation(summary = "Status der WhatsApp-Anbindung",
            description = "configured zeigt, ob Zugangsdaten gesetzt sind; messageCount, wie viele empfangene "
                    + "Nachrichten die angemeldete Person sehen darf.")
    public Status status() {
        FamilyMember viewer = currentMember.get();
        long count;
        if (access.seesAll(viewer)) {
            count = service.count();
        } else {
            count = access.seesOwn(viewer) && viewer.phone() != null ? service.countFrom(viewer.phone()) : 0;
        }
        return new Status(properties.isConfigured(), count);
    }

    @GetMapping("/messages")
    @Operation(summary = "Empfangene WhatsApp-Nachrichten",
            description = "Die letzten 100, neueste zuerst, inkl. des rohen Payloads. Jede Person sieht die Nachrichten "
                    + "von ihrer eigenen Handynummer, Administratoren alle; ohne eingetragene Nummer ist die Liste leer.")
    public List<WhatsAppMessage> messages() {
        FamilyMember viewer = currentMember.get();
        access.requireView(viewer);
        if (access.seesAll(viewer)) {
            return service.recent();
        }
        return viewer.phone() == null ? List.of() : service.recentFrom(viewer.phone());
    }
}
