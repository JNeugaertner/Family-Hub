package de.familyhub.rewards;

import static de.familyhub.permission.Action.ANSEHEN;
import static de.familyhub.permission.Action.FREIGEBEN;
import static de.familyhub.permission.Action.VERWALTEN;
import static de.familyhub.permission.Action.VORSCHLAGEN;
import static de.familyhub.permission.Module.PUNKTE;

import org.springframework.stereotype.Component;

import de.familyhub.family.FamilyMember;
import de.familyhub.permission.Permissions;
import de.familyhub.permission.RoleResolver;
import de.familyhub.permission.Scope;
import de.familyhub.web.ApiException;

// Rechte im Belohnungsshop (Entscheidungen vom 28.09.2026):
// - Belohnungen sehen: wer Punkte sehen darf ("punkte/ansehen"); inaktive nur, wer verwalten darf.
// - Einlösen: Kinder und Jugendliche nur für sich selbst mit "punkte/vorschlagen/eigen".
// - Genehmigen und ablehnen: "punkte/freigeben/familie".
// - Belohnungen anlegen, ändern, löschen: "punkte/verwalten/familie" (Administratoren).
@Component
public class RewardAccess {

    private final Permissions permissions;
    private final RoleResolver roles;

    public RewardAccess(Permissions permissions, RoleResolver roles) {
        this.permissions = permissions;
        this.roles = roles;
    }

    public void requireView(FamilyMember viewer) {
        permissions.require(viewer, PUNKTE, ANSEHEN, Scope.EIGEN, "Keine Berechtigung, Belohnungen anzusehen.");
    }

    public boolean seesFamily(FamilyMember viewer) {
        return permissions.can(viewer, PUNKTE, ANSEHEN, Scope.FAMILIE);
    }

    public boolean mayManage(FamilyMember viewer) {
        return permissions.can(viewer, PUNKTE, VERWALTEN, Scope.FAMILIE);
    }

    public void requireManage(FamilyMember viewer) {
        permissions.require(viewer, PUNKTE, VERWALTEN, Scope.FAMILIE, "Nur Administratoren dürfen Belohnungen verwalten.");
    }

    public void requireRedeem(FamilyMember viewer, String memberId) {
        if (!viewer.id().equals(memberId)) {
            throw ApiException.forbidden("Du darfst Belohnungen nur für dich selbst einlösen.");
        }
        if (!roles.effectiveRole(viewer).countsAsChild()) {
            throw ApiException.forbidden("Nur Kinder und Jugendliche dürfen Belohnungen einlösen.");
        }
        permissions.require(viewer, PUNKTE, VORSCHLAGEN, Scope.EIGEN,
                "Keine Berechtigung, Belohnungen einzulösen.");
    }

    public boolean mayDecide(FamilyMember viewer) {
        return permissions.can(viewer, PUNKTE, FREIGEBEN, Scope.FAMILIE);
    }

    public void requireDecide(FamilyMember viewer) {
        permissions.require(viewer, PUNKTE, FREIGEBEN, Scope.FAMILIE,
                "Nur Administratoren dürfen Einlösungen genehmigen oder ablehnen.");
    }
}
