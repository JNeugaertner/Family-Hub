package de.familyhub.whatsapp;

import java.util.Optional;

import org.springframework.stereotype.Component;

import de.familyhub.family.FamilyMember;
import de.familyhub.inbox.Channel;
import de.familyhub.inbox.SenderIdentity;

// Bei WhatsApp schreibt ein Familienmitglied unter seiner Handynummer (vom Administrator eingetragen).
@Component
class WhatsAppSenderIdentity implements SenderIdentity {

    @Override
    public Optional<String> senderKeyOf(FamilyMember member) {
        return Optional.ofNullable(member.phone()).map(Channel.WHATSAPP::prefixed);
    }
}
