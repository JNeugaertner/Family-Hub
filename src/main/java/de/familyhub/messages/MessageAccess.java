package de.familyhub.messages;

import static de.familyhub.permission.Action.ANSEHEN;
import static de.familyhub.permission.Action.ERSTELLEN;
import static de.familyhub.permission.Action.LOESCHEN;
import static de.familyhub.permission.Module.MESSENGER;

import org.springframework.stereotype.Component;

import de.familyhub.family.FamilyMember;
import de.familyhub.family.FamilyMemberRepository;
import de.familyhub.permission.Action;
import de.familyhub.permission.Permissions;
import de.familyhub.permission.Scope;
import de.familyhub.web.ApiException;

// Rechte an Nachrichten (Entscheidungen vom 01.10.2026: Familiengruppe und Einzelchats für alle außer Gästen):
// - Familiengruppe lesen und schreiben: "messenger/ansehen/familie" bzw. "messenger/erstellen/familie".
// - Einzelchats: "messenger/ansehen/eigen" bzw. "messenger/erstellen/eigen". Einen Einzelchat sehen nur die beiden
//   Beteiligten, auch Administratoren nicht.
// - Eigene Nachrichten löschen: "messenger/loeschen/eigen"; fremde in der Familiengruppe: "messenger/loeschen/familie".
@Component
public class MessageAccess {

    private final Permissions permissions;
    private final FamilyMemberRepository members;

    public MessageAccess(Permissions permissions, FamilyMemberRepository members) {
        this.permissions = permissions;
        this.members = members;
    }

    public void requireMessenger(FamilyMember viewer) {
        require(viewer, ANSEHEN, Scope.EIGEN, "Keine Berechtigung für Nachrichten.");
    }

    public boolean mayReadFamily(FamilyMember member) {
        return permissions.can(member, MESSENGER, ANSEHEN, Scope.FAMILIE);
    }

    // Wer überhaupt Nachrichten hat, ist als Partner für Einzelchats wählbar
    public boolean mayChat(FamilyMember member) {
        return permissions.can(member, MESSENGER, ANSEHEN, Scope.EIGEN);
    }

    public boolean canRead(FamilyMember viewer, String conversation) {
        if (!mayChat(viewer)) {
            return false;
        }
        if (Conversations.isFamily(conversation)) {
            return mayReadFamily(viewer);
        }
        return Conversations.partnerOf(conversation, viewer.id())
                .flatMap(members::findById)
                .filter(this::mayChat)
                .isPresent();
    }

    // Eine Unterhaltung, die jemand nicht lesen darf, gilt für ihn als nicht vorhanden.
    public void requireRead(FamilyMember viewer, String conversation) {
        requireMessenger(viewer);
        if (!canRead(viewer, conversation)) {
            throw ApiException.notFound("Unterhaltung " + conversation + " existiert nicht.");
        }
    }

    public void requireWrite(FamilyMember viewer, String conversation) {
        requireRead(viewer, conversation);
        Scope scope = Conversations.isFamily(conversation) ? Scope.FAMILIE : Scope.EIGEN;
        require(viewer, ERSTELLEN, scope, "Keine Berechtigung, hier zu schreiben.");
    }

    public void requireDelete(FamilyMember viewer, ChatMessage message) {
        if (viewer.id().equals(message.senderId())) {
            require(viewer, LOESCHEN, Scope.EIGEN, "Keine Berechtigung, Nachrichten zu löschen.");
        } else if (Conversations.isFamily(message.conversation())) {
            require(viewer, LOESCHEN, Scope.FAMILIE, "Nur Administratoren dürfen fremde Nachrichten löschen.");
        } else {
            throw ApiException.forbidden("In einem Einzelchat kann man nur eigene Nachrichten löschen.");
        }
    }

    private void require(FamilyMember viewer, Action action, Scope scope, String message) {
        permissions.require(viewer, MESSENGER, action, scope, message);
    }
}
