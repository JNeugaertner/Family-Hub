package de.familyhub.messages;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import org.springframework.stereotype.Service;

import de.familyhub.family.FamilyMember;
import de.familyhub.family.FamilyMemberRepository;

@Service
public class MessageService {

    private final ChatMessageRepository messages;
    private final ReadMarkRepository reads;
    private final FamilyMemberRepository members;
    private final MessageAccess access;
    private final Clock clock;

    public MessageService(ChatMessageRepository messages, ReadMarkRepository reads, FamilyMemberRepository members,
            MessageAccess access, Clock clock) {
        this.messages = messages;
        this.reads = reads;
        this.members = members;
        this.access = access;
        this.clock = clock;
    }

    // kind: family oder direct; partnerId nur bei Einzelchats; lastMessage fehlt, solange niemand geschrieben hat
    public record ConversationSummary(String id, String kind, String partnerId, ChatMessage lastMessage, long unread) {
    }

    // Die Familiengruppe und ein Einzelchat mit jedem, der Nachrichten hat (auch ohne bisherige Nachricht)
    public List<ConversationSummary> conversationsOf(FamilyMember viewer) {
        List<ConversationSummary> result = new ArrayList<>();
        if (access.mayReadFamily(viewer)) {
            result.add(summary(viewer, Conversations.FAMILY, "family", null));
        }
        for (FamilyMember partner : members.findAll()) {
            if (!partner.id().equals(viewer.id()) && access.mayChat(partner)) {
                result.add(summary(viewer, Conversations.direct(viewer.id(), partner.id()), "direct", partner.id()));
            }
        }
        return result;
    }

    // Die letzten 200 Nachrichten, älteste zuerst
    public List<ChatMessage> latest(String conversation) {
        List<ChatMessage> latest = new ArrayList<>(messages.findTop200ByConversationOrderBySentAtDescIdDesc(conversation));
        Collections.reverse(latest);
        return latest;
    }

    public ChatMessage send(FamilyMember sender, String conversation, String text) {
        LocalDateTime now = LocalDateTime.now(clock);
        ChatMessage saved = messages.save(new ChatMessage(null, conversation, sender.id(), text.strip(), now));
        // Wer schreibt, hat die Unterhaltung bis hierhin gelesen
        reads.save(ReadMark.of(sender.id(), conversation, now));
        return saved;
    }

    public void markRead(FamilyMember viewer, String conversation) {
        reads.save(ReadMark.of(viewer.id(), conversation, LocalDateTime.now(clock)));
    }

    // Beim Löschen eines Mitglieds: seine Einzelchats verschwinden ganz; was es in der Familiengruppe geschrieben
    // hat, bleibt für die anderen lesbar (ohne Namen, die Oberfläche zeigt "Ehemaliges Mitglied").
    public void forgetMember(String memberId) {
        messages.deleteByConversationContaining(memberId);
        reads.deleteByConversationContaining(memberId);
        reads.deleteByMemberId(memberId);
    }

    private ConversationSummary summary(FamilyMember viewer, String conversation, String kind, String partnerId) {
        ChatMessage last = messages.findFirstByConversationOrderBySentAtDescIdDesc(conversation).orElse(null);
        long unread = last == null ? 0 : reads.findById(ReadMark.idFor(viewer.id(), conversation))
                .map(mark -> messages.countByConversationAndSenderIdNotAndSentAtAfter(conversation, viewer.id(),
                        mark.readAt()))
                .orElseGet(() -> messages.countByConversationAndSenderIdNot(conversation, viewer.id()));
        return new ConversationSummary(conversation, kind, partnerId, last, unread);
    }
}
