package de.familyhub.inbox;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.familyhub.family.FamilyMember;
import de.familyhub.security.CurrentMember;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/inbox")
@Tag(name = "Eingang (WhatsApp, Telegram)")
public class InboxController {

    private final Inbox inbox;
    private final InboxAccess access;
    private final CurrentMember currentMember;

    public InboxController(Inbox inbox, InboxAccess access, CurrentMember currentMember) {
        this.inbox = inbox;
        this.access = access;
        this.currentMember = currentMember;
    }

    @GetMapping
    @Operation(summary = "Empfangene Nachrichten von WhatsApp und Telegram",
            description = "Die letzten 100, neueste zuerst, inkl. Rohdaten. Jede Person sieht ihre eigenen (WhatsApp: "
                    + "über die Handynummer, Telegram: über das verknüpfte Konto), Administratoren alle.")
    public List<InboundMessage> list(
            @Parameter(description = "Nur diesen Kanal, z. B. telegram") @RequestParam(required = false) Channel channel) {
        FamilyMember viewer = currentMember.get();
        access.requireView(viewer);
        return inbox.visibleFor(viewer, channel);
    }
}
