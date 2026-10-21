package de.familyhub.telegram;

import java.util.Optional;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import de.familyhub.family.FamilyMember;
import de.familyhub.inbox.Channel;
import de.familyhub.inbox.Inbox;
import de.familyhub.inbox.InboxAccess;
import de.familyhub.security.CurrentMember;
import de.familyhub.web.ApiException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

// Das eigene Telegram-Konto mit FamilyHub verknüpfen. Ablauf: POST /link liefert einen Link t.me/<bot>?start=<code>;
// wer ihn öffnet und in Telegram auf „Starten“ tippt, ist verknüpft (TelegramService). Verknüpfen darf, wer
// Nachrichten hat, also alle außer Gästen (wie beim Familien-Chat).
@RestController
@RequestMapping("/api/telegram")
@Tag(name = "Telegram")
public class TelegramController {

    static final String TEST_MESSAGE = "👋 Testnachricht von FamilyHub. Hier kommen künftig deine Benachrichtigungen an.";

    private final TelegramProperties properties;
    private final TelegramService service;
    private final Inbox inbox;
    private final InboxAccess access;
    private final CurrentMember currentMember;

    public TelegramController(TelegramProperties properties, TelegramService service, Inbox inbox, InboxAccess access,
            CurrentMember currentMember) {
        this.properties = properties;
        this.service = service;
        this.inbox = inbox;
        this.access = access;
        this.currentMember = currentMember;
    }

    // botUsername: der Bot in Telegram (null, solange er nicht erreichbar ist; der Grund steht in error)
    public record Status(boolean configured, String botUsername, boolean linked, String linkedName, long messageCount,
            String error) {
    }

    public record LinkResponse(String url) {
    }

    @GetMapping("/status")
    @Operation(summary = "Status von Telegram für die angemeldete Person",
            description = "configured: Bot-Token ist gesetzt; linked: eigenes Telegram-Konto verknüpft; messageCount: "
                    + "empfangene Telegram-Nachrichten, die die Person sehen darf.")
    public Status status() {
        FamilyMember viewer = currentMember.get();
        String bot = null;
        String error = null;
        if (properties.isConfigured()) {
            try {
                bot = service.botUsername();
            } catch (TelegramException e) {
                error = e.getMessage();
            }
        }
        Optional<TelegramLink> link = service.linkOf(viewer);
        return new Status(properties.isConfigured(), bot, link.isPresent(),
                link.map(TelegramLink::telegramName).orElse(null), inbox.countVisibleFor(viewer, Channel.TELEGRAM),
                error);
    }

    @PostMapping("/link")
    @Operation(summary = "Telegram verbinden",
            description = "Liefert den Link zum Bot mit einem Einmal-Code (zehn Minuten gültig). Das Frontend öffnet "
                    + "ihn; in Telegram auf „Starten“ tippen verknüpft das Konto.")
    public LinkResponse link() {
        FamilyMember viewer = currentMember.get();
        access.requireView(viewer);
        requireConfigured();
        try {
            return new LinkResponse(service.linkUrl(viewer));
        } catch (TelegramException e) {
            throw ApiException.unavailable(e.getMessage());
        }
    }

    @DeleteMapping("/link")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Telegram trennen", description = "FamilyHub schickt keine Nachrichten mehr an das Konto und "
            + "verwirft neue Nachrichten von dort. Bereits empfangene bleiben im Eingang.")
    public void unlink() {
        service.unlink(currentMember.get());
    }

    @PostMapping("/test")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Testnachricht an das eigene Telegram-Konto senden")
    public void sendTest() {
        FamilyMember viewer = currentMember.get();
        requireConfigured();
        boolean sent;
        try {
            sent = service.sendTo(viewer, TEST_MESSAGE);
        } catch (TelegramException e) {
            throw ApiException.unavailable(e.getMessage());
        }
        if (!sent) {
            throw ApiException.conflict("Es ist noch kein Telegram-Konto verbunden.");
        }
    }

    private void requireConfigured() {
        if (!properties.isConfigured()) {
            throw ApiException.conflict("Telegram ist nicht eingerichtet.");
        }
    }
}
