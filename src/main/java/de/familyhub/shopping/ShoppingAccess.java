package de.familyhub.shopping;

import static de.familyhub.permission.Action.ANSEHEN;

import static de.familyhub.permission.Action.BEARBEITEN;
import static de.familyhub.permission.Action.ERSTELLEN;
import static de.familyhub.permission.Action.FREIGEBEN;
import static de.familyhub.permission.Action.LOESCHEN;
import static de.familyhub.permission.Action.VORSCHLAGEN;
import static de.familyhub.permission.Module.EINKAUF;

import java.util.function.Predicate;

import org.springframework.stereotype.Component;

import de.familyhub.family.FamilyMember;
import de.familyhub.permission.Action;
import de.familyhub.permission.Permissions;
import de.familyhub.permission.Scope;
import de.familyhub.web.ApiException;

// Rechte an der Einkaufsliste (Entscheidungen vom 28.09.2026, Rollenkonzept):
// - Ansehen: "einkauf/ansehen" (Administratoren, Jugendliche, Kinder). Offene Vorschläge sehen nur, wer sie gemacht
//   hat, und wer sie freigeben darf.
// - Direkt auf die Liste: "einkauf/erstellen"; wer nur "einkauf/vorschlagen" hat (Kinder), macht einen Vorschlag.
// - Ändern und abhaken: "einkauf/bearbeiten"; löschen: "einkauf/loeschen" (eigene Vorschläge darf man zurückziehen).
// - Vorschläge übernehmen oder ablehnen: "einkauf/freigeben" (nur Administratoren).
@Component
public class ShoppingAccess {

    private final Permissions permissions;

    public ShoppingAccess(Permissions permissions) {
        this.permissions = permissions;
    }

    public Predicate<ShoppingItem> visibilityFor(FamilyMember viewer) {
        requireView(viewer);
        boolean decides = can(viewer, FREIGEBEN);
        return item -> !item.isProposal() || decides || viewer.id().equals(item.createdBy());
    }

    public void requireView(FamilyMember viewer) {
        require(viewer, ANSEHEN, "Keine Berechtigung, die Einkaufsliste anzusehen.");
    }

    public ShoppingItemStatus statusForNewItem(FamilyMember viewer) {
        if (can(viewer, ERSTELLEN)) {
            return ShoppingItemStatus.APPROVED;
        }
        if (can(viewer, VORSCHLAGEN)) {
            return ShoppingItemStatus.PROPOSED;
        }
        throw ApiException.forbidden("Keine Berechtigung, Artikel hinzuzufügen.");
    }

    public void requireEdit(FamilyMember viewer) {
        require(viewer, BEARBEITEN, "Keine Berechtigung, die Einkaufsliste zu ändern.");
    }

    public void requireDelete(FamilyMember viewer, ShoppingItem item) {
        if (item.isProposal() && viewer.id().equals(item.createdBy())) {
            return;
        }
        require(viewer, LOESCHEN, "Keine Berechtigung, Artikel zu löschen.");
    }

    public void requireDeleteChecked(FamilyMember viewer) {
        require(viewer, LOESCHEN, "Keine Berechtigung, Artikel zu löschen.");
    }

    public void requireDecision(FamilyMember viewer) {
        require(viewer, FREIGEBEN, "Nur Administratoren dürfen Vorschläge übernehmen oder ablehnen.");
    }

    private boolean can(FamilyMember viewer, Action action) {
        return permissions.can(viewer, EINKAUF, action, Scope.FAMILIE);
    }

    private void require(FamilyMember viewer, Action action, String message) {
        permissions.require(viewer, EINKAUF, action, Scope.FAMILIE, message);
    }
}
