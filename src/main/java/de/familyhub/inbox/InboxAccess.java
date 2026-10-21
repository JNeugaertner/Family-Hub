package de.familyhub.inbox;

import static de.familyhub.permission.Action.ANSEHEN;
import static de.familyhub.permission.Action.VERWALTEN;
import static de.familyhub.permission.Module.MESSENGER;

import org.springframework.stereotype.Component;

import de.familyhub.family.FamilyMember;
import de.familyhub.permission.Permissions;
import de.familyhub.permission.Scope;

// Wer welche Nachrichten aus dem Eingang sieht (Entscheidung vom 20.10.2026, für alle Kanäle): jede Person die,
// die sie selbst geschrieben hat ("messenger/ansehen/eigen", zugeordnet über SenderIdentity); wer Nachrichten
// verwalten darf, alle ("messenger/verwalten/familie", standardmäßig Administratoren). Gäste sehen keine.
@Component
public class InboxAccess {

    private final Permissions permissions;

    public InboxAccess(Permissions permissions) {
        this.permissions = permissions;
    }

    public boolean seesAll(FamilyMember viewer) {
        return permissions.can(viewer, MESSENGER, VERWALTEN, Scope.FAMILIE);
    }

    public boolean seesOwn(FamilyMember viewer) {
        return permissions.can(viewer, MESSENGER, ANSEHEN, Scope.EIGEN);
    }

    public void requireView(FamilyMember viewer) {
        permissions.require(viewer, MESSENGER, ANSEHEN, Scope.EIGEN,
                "Keine Berechtigung für Nachrichten von WhatsApp und Telegram.");
    }
}
