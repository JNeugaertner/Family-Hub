package de.familyhub.meals;

import static de.familyhub.permission.Action.ANSEHEN;
import static de.familyhub.permission.Action.BEARBEITEN;
import static de.familyhub.permission.Action.FREIGEBEN;
import static de.familyhub.permission.Action.VORSCHLAGEN;
import static de.familyhub.permission.Module.ESSEN;

import java.util.function.Predicate;

import org.springframework.stereotype.Component;

import de.familyhub.family.FamilyMember;
import de.familyhub.permission.Action;
import de.familyhub.permission.Permissions;
import de.familyhub.permission.Scope;
import de.familyhub.web.ApiException;

// Rechte am Essensplan (Entscheidungen vom 28.09.2026):
// - Ansehen (Plan und Gerichte-Sammlung): "essen/ansehen" (Administratoren, Jugendliche, Kinder). Offene Wünsche
//   sehen nur, wer sie geäußert hat, und wer sie freigeben darf.
// - Plan und Gerichte-Sammlung pflegen (anlegen, ändern, löschen): "essen/bearbeiten" (Eltern und Jugendliche).
// - Wer nur "essen/vorschlagen" hat (Kinder), äußert einen Wunsch; eigene Wünsche darf man zurückziehen.
// - Wünsche übernehmen oder ablehnen: "essen/freigeben" (nur Administratoren).
@Component
public class MealAccess {

    private final Permissions permissions;

    public MealAccess(Permissions permissions) {
        this.permissions = permissions;
    }

    public Predicate<MealEntry> visibilityFor(FamilyMember viewer) {
        requireView(viewer);
        boolean decides = can(viewer, FREIGEBEN);
        return entry -> !entry.isProposal() || decides || viewer.id().equals(entry.createdBy());
    }

    public void requireView(FamilyMember viewer) {
        require(viewer, ANSEHEN, "Keine Berechtigung, den Essensplan anzusehen.");
    }

    public MealStatus statusForNewEntry(FamilyMember viewer) {
        if (can(viewer, BEARBEITEN)) {
            return MealStatus.APPROVED;
        }
        if (can(viewer, VORSCHLAGEN)) {
            return MealStatus.PROPOSED;
        }
        throw ApiException.forbidden("Keine Berechtigung, den Essensplan zu ändern.");
    }

    public void requireDelete(FamilyMember viewer, MealEntry entry) {
        if (entry.isProposal() && viewer.id().equals(entry.createdBy())) {
            return;
        }
        require(viewer, BEARBEITEN, "Keine Berechtigung, den Essensplan zu ändern.");
    }

    public void requireEditDishes(FamilyMember viewer) {
        require(viewer, BEARBEITEN, "Keine Berechtigung, Gerichte zu verwalten.");
    }

    public void requireDecision(FamilyMember viewer) {
        require(viewer, FREIGEBEN, "Nur Administratoren dürfen Wünsche übernehmen oder ablehnen.");
    }

    private boolean can(FamilyMember viewer, Action action) {
        return permissions.can(viewer, ESSEN, action, Scope.FAMILIE);
    }

    private void require(FamilyMember viewer, Action action, String message) {
        permissions.require(viewer, ESSEN, action, Scope.FAMILIE, message);
    }
}
