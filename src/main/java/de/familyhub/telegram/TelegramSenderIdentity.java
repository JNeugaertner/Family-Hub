package de.familyhub.telegram;

import java.util.Optional;

import org.springframework.stereotype.Component;

import de.familyhub.family.FamilyMember;
import de.familyhub.inbox.Channel;
import de.familyhub.inbox.SenderIdentity;

// Bei Telegram schreibt ein Familienmitglied unter der Nutzer-ID des verknüpften Kontos.
@Component
class TelegramSenderIdentity implements SenderIdentity {

    private final TelegramLinkRepository links;

    TelegramSenderIdentity(TelegramLinkRepository links) {
        this.links = links;
    }

    @Override
    public Optional<String> senderKeyOf(FamilyMember member) {
        return links.findByMemberId(member.id())
                .map(link -> Channel.TELEGRAM.prefixed(String.valueOf(link.telegramUserId())));
    }
}
