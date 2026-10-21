package de.familyhub.notifications;

import java.util.List;

import org.springframework.stereotype.Component;

import de.familyhub.family.FamilyMember;
import de.familyhub.family.FamilyMemberRepository;
import de.familyhub.messages.ChatMessage;
import de.familyhub.messages.Conversations;
import de.familyhub.messages.MessageAccess;

// Chat-Nachrichten (Entscheidung vom 21.10.2026): Jede neue Nachricht geht an alle, die die Unterhaltung lesen
// dürfen, außer an die Person, die sie geschrieben hat. Aus einem Einzelchat bekommt sie also nur die andere Person,
// aus der Familiengruppe alle mit Nachrichten. Antworten in Telegram landen im Eingang, nicht im Chat.
@Component
public class MessageNotifications {

    private final Notifications notifications;
    private final FamilyMemberRepository members;
    private final MessageAccess access;

    public MessageNotifications(Notifications notifications, FamilyMemberRepository members, MessageAccess access) {
        this.notifications = notifications;
        this.members = members;
        this.access = access;
    }

    public void sent(ChatMessage message, FamilyMember sender) {
        String conversation = message.conversation();
        boolean family = Conversations.isFamily(conversation);
        List<FamilyMember> recipients = family
                ? members.findAll()
                : Conversations.partnerOf(conversation, sender.id()).flatMap(members::findById).stream().toList();
        String text = "💬 " + sender.name() + (family ? " in der Familiengruppe: " : ": ") + message.text();
        recipients.stream()
                .filter(member -> !member.id().equals(sender.id()))
                .filter(member -> access.canRead(member, conversation))
                .forEach(member -> notifications.send(member, NotificationKind.MESSAGES, text));
    }
}
