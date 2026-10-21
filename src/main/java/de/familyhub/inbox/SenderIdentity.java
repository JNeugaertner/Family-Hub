package de.familyhub.inbox;

import java.util.Optional;

import de.familyhub.family.FamilyMember;

// Jeder Kanal sagt, unter welcher Kennung ein Familienmitglied dort schreibt (WhatsApp: Handynummer, Telegram:
// verknüpftes Konto). Darüber sieht jede Person ihre eigenen Nachrichten, ohne dass der Eingang die Kanäle kennt.
public interface SenderIdentity {

    // senderKey wie in InboundMessage (Channel.prefixed), leer, wenn die Person in diesem Kanal nicht bekannt ist
    Optional<String> senderKeyOf(FamilyMember member);
}
