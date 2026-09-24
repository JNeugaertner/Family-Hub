package de.familyhub.calendar;

import static de.familyhub.permission.Action.ANSEHEN;
import static de.familyhub.permission.Action.BEARBEITEN;
import static de.familyhub.permission.Action.ERSTELLEN;
import static de.familyhub.permission.Action.LOESCHEN;
import static de.familyhub.permission.Module.KALENDER;

import org.springframework.stereotype.Component;

import de.familyhub.family.FamilyMember;
import de.familyhub.permission.Action;
import de.familyhub.permission.Permissions;
import de.familyhub.permission.Scope;
import de.familyhub.web.ApiException;

// Wer welche Termine sehen und ändern darf. Eigene Termine (memberId = angemeldete Person) brauchen
// Rechte im Geltungsbereich "eigen", Termine anderer Familienmitglieder im Bereich "familie".
@Component
public class CalendarAccess {

    private final Permissions permissions;

    public CalendarAccess(Permissions permissions) {
        this.permissions = permissions;
    }

    public boolean canSee(FamilyMember viewer, CalendarEvent event) {
        if (permissions.can(viewer, KALENDER, ANSEHEN, Scope.FAMILIE)) {
            return true;
        }
        return isOwn(viewer, event.memberId()) && permissions.can(viewer, KALENDER, ANSEHEN, Scope.EIGEN);
    }

    public void requireCreate(FamilyMember viewer, CalendarEvent event) {
        require(viewer, ERSTELLEN, scopeOf(viewer, event.memberId()), "Termine anzulegen", "anlegen");
    }

    public void requireUpdate(FamilyMember viewer, CalendarEvent existing, CalendarEvent changed) {
        Scope scope = isOwn(viewer, existing.memberId()) && isOwn(viewer, changed.memberId())
                ? Scope.EIGEN : Scope.FAMILIE;
        require(viewer, BEARBEITEN, scope, "Termine zu ändern", "ändern");
    }

    public void requireDelete(FamilyMember viewer, CalendarEvent existing) {
        require(viewer, LOESCHEN, scopeOf(viewer, existing.memberId()), "Termine zu löschen", "löschen");
    }

    private void require(FamilyMember viewer, Action action, Scope scope, String what, String verb) {
        if (permissions.can(viewer, KALENDER, action, scope)) {
            return;
        }
        if (scope == Scope.FAMILIE && permissions.can(viewer, KALENDER, action, Scope.EIGEN)) {
            throw ApiException.forbidden("Du darfst nur deine eigenen Termine " + verb + ".");
        }
        throw ApiException.forbidden("Keine Berechtigung, " + what + ".");
    }

    private static Scope scopeOf(FamilyMember viewer, String memberId) {
        return isOwn(viewer, memberId) ? Scope.EIGEN : Scope.FAMILIE;
    }

    private static boolean isOwn(FamilyMember viewer, String memberId) {
        return viewer.id().equals(memberId);
    }
}
