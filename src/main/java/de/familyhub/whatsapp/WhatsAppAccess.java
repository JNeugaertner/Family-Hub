package de.familyhub.whatsapp;

import static de.familyhub.permission.Action.ANSEHEN;
import static de.familyhub.permission.Action.VERWALTEN;
import static de.familyhub.permission.Module.MESSENGER;

import org.springframework.stereotype.Component;

import de.familyhub.family.FamilyMember;
import de.familyhub.permission.Permissions;
import de.familyhub.permission.Scope;

// Wer welche WhatsApp-Nachrichten sieht (Entscheidung vom 20.10.2026): jede Person die von ihrer eigenen
// Handynummer ("messenger/ansehen/eigen", die Nummer pflegen Administratoren am Familienmitglied); wer Nachrichten
// verwalten darf, alle ("messenger/verwalten/familie", standardmäßig Administratoren). Gäste sehen keine.
@Component
public class WhatsAppAccess {

    private final Permissions permissions;

    public WhatsAppAccess(Permissions permissions) {
        this.permissions = permissions;
    }

    public boolean seesAll(FamilyMember viewer) {
        return permissions.can(viewer, MESSENGER, VERWALTEN, Scope.FAMILIE);
    }

    public boolean seesOwn(FamilyMember viewer) {
        return permissions.can(viewer, MESSENGER, ANSEHEN, Scope.EIGEN);
    }

    public void requireView(FamilyMember viewer) {
        permissions.require(viewer, MESSENGER, ANSEHEN, Scope.EIGEN, "Keine Berechtigung für WhatsApp-Nachrichten.");
    }
}
