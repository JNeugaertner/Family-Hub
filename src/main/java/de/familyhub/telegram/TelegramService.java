package de.familyhub.telegram;

import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import de.familyhub.family.FamilyMember;
import de.familyhub.family.FamilyMemberRepository;
import de.familyhub.inbox.Channel;
import de.familyhub.inbox.InboundMessage;
import de.familyhub.inbox.Inbox;
import de.familyhub.live.LiveUpdates;
import tools.jackson.databind.JsonNode;

// Der Telegram-Bot der Familie in beide Richtungen (Entscheidung vom 21.10.2026):
// - Empfangen: Nachrichten verknüpfter Familienmitglieder landen roh im gemeinsamen Eingang. Nachrichten von fremden
//   Telegram-Konten verwirft FamilyHub, ohne sie zu speichern; der Bot ist öffentlich auffindbar.
// - Verknüpfen: "/start <code>" mit einem Einmal-Code aus FamilyHub (TelegramLinkCodes).
// - Senden: an den privaten Chat einer verknüpften Person. Antworten auf eingehende Nachrichten gibt es nicht, nur
//   Rückmeldungen zum Verknüpfen.
@Service
public class TelegramService {

    private static final Logger LOG = LoggerFactory.getLogger(TelegramService.class);

    static final String START = "/start";

    private final TelegramApi api;
    private final TelegramProperties properties;
    private final TelegramLinkRepository links;
    private final TelegramLinkCodes codes;
    private final FamilyMemberRepository members;
    private final Inbox inbox;
    private final LiveUpdates live;
    private final Clock clock;

    // Benutzername des Bots für den Link t.me/<name>; ändert sich nicht, solange der Token gleich bleibt
    private volatile String botUsername;

    public TelegramService(TelegramApi api, TelegramProperties properties, TelegramLinkRepository links,
            TelegramLinkCodes codes, FamilyMemberRepository members, Inbox inbox, LiveUpdates live, Clock clock) {
        this.api = api;
        this.properties = properties;
        this.links = links;
        this.codes = codes;
        this.members = members;
        this.inbox = inbox;
        this.live = live;
        this.clock = clock;
    }

    public boolean isConfigured() {
        return properties.isConfigured();
    }

    public String botUsername() {
        if (botUsername == null && properties.isConfigured()) {
            botUsername = api.getMe().username();
        }
        return botUsername;
    }

    // Adresse, über die die Person den Bot startet und sich damit verknüpft
    public String linkUrl(FamilyMember member) {
        return "https://t.me/" + botUsername() + "?start=" + codes.create(member.id());
    }

    public Optional<TelegramLink> linkOf(FamilyMember member) {
        return links.findByMemberId(member.id());
    }

    public void unlink(FamilyMember member) {
        links.deleteByMemberId(member.id());
    }

    // Beim Löschen eines Familienmitglieds
    public void forgetMember(String memberId) {
        links.deleteByMemberId(memberId);
    }

    // false, wenn die Person kein Telegram-Konto verknüpft hat
    public boolean sendTo(FamilyMember member, String text) {
        Optional<TelegramLink> link = links.findByMemberId(member.id());
        link.ifPresent(l -> api.sendMessage(l.chatId(), text));
        return link.isPresent();
    }

    // Eine Nachricht, die der Abholer (TelegramPoller) von Telegram bekommen hat
    void handle(JsonNode update) {
        JsonNode message = update.path("message");
        long userId = message.path("from").path("id").asLong(0);
        if (!message.isObject() || userId == 0) {
            return;
        }
        long chatId = message.path("chat").path("id").asLong();
        String text = textOrNull(message, "text");
        if (text != null && (text.equals(START) || text.startsWith(START + " "))) {
            link(message, userId, chatId, text.substring(START.length()).strip());
            return;
        }
        Optional<TelegramLink> link = links.findByTelegramUserId(userId);
        if (link.isEmpty()) {
            LOG.debug("Telegram-Nachricht von einem nicht verknüpften Konto verworfen");
            return;
        }
        long seconds = message.path("date").asLong(0);
        InboundMessage inbound = InboundMessage.of(Channel.TELEGRAM,
                chatId + ":" + message.path("message_id").asLong(), String.valueOf(userId), nameOf(message), text,
                seconds > 0 ? Instant.ofEpochSecond(seconds) : null, clock.instant(), update.toString());
        if (inbox.store(inbound)) {
            LOG.info("Eingehende Telegram-Nachricht von Mitglied {} gespeichert", link.get().memberId());
            live.publish("telegram");
        }
    }

    private void link(JsonNode message, long userId, long chatId, String code) {
        if (!"private".equals(textOrNull(message.path("chat"), "type"))) {
            return;
        }
        if (code.isEmpty()) {
            reply(chatId, "Hallo! Verbinde dein Telegram-Konto in FamilyHub unter „Familie“ → „Telegram“ → "
                    + "„Mit Telegram verbinden“.");
            return;
        }
        Optional<FamilyMember> member = codes.redeem(code).flatMap(members::findById);
        if (member.isEmpty()) {
            reply(chatId, "Dieser Link ist abgelaufen oder wurde schon benutzt. Bitte in FamilyHub noch einmal auf "
                    + "„Mit Telegram verbinden“ tippen.");
            return;
        }
        // Jede Person hat höchstens ein Konto, jedes Konto gehört zu höchstens einer Person: das neue gewinnt
        links.findByTelegramUserId(userId).ifPresent(links::delete);
        links.findByMemberId(member.get().id()).ifPresent(links::delete);
        links.save(new TelegramLink(null, member.get().id(), userId, chatId, nameOf(message), clock.instant()));
        LOG.info("Telegram-Konto mit Mitglied {} verknüpft", member.get().id());
        reply(chatId, "Verbunden! Dieses Telegram-Konto gehört jetzt zu " + member.get().name()
                + " in FamilyHub. Hier bekommst du künftig Nachrichten von FamilyHub.");
        live.publish("telegram");
    }

    // Rückmeldungen sind nicht kritisch: Klappt das Senden nicht, bleibt die Verknüpfung trotzdem bestehen
    private void reply(long chatId, String text) {
        try {
            api.sendMessage(chatId, text);
        } catch (TelegramException e) {
            LOG.warn("Antwort an Telegram fehlgeschlagen: {}", e.getMessage());
        }
    }

    private static String nameOf(JsonNode message) {
        JsonNode from = message.path("from");
        String first = textOrNull(from, "first_name");
        String last = textOrNull(from, "last_name");
        if (first != null && last != null) {
            return first + " " + last;
        }
        return first != null ? first : textOrNull(from, "username");
    }

    private static String textOrNull(JsonNode node, String field) {
        JsonNode value = node.path(field);
        return value.isString() ? value.stringValue() : null;
    }
}
