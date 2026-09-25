package de.familyhub.task;

import static de.familyhub.permission.Action.ANSEHEN;
import static de.familyhub.permission.Action.BEARBEITEN;
import static de.familyhub.permission.Action.ERSTELLEN;
import static de.familyhub.permission.Action.FREIGEBEN;
import static de.familyhub.permission.Action.LOESCHEN;
import static de.familyhub.permission.Module.AUFGABEN;
import static de.familyhub.permission.Module.PUNKTE;

import java.util.function.Predicate;

import org.springframework.stereotype.Component;

import de.familyhub.family.FamilyMember;
import de.familyhub.permission.Action;
import de.familyhub.permission.Permissions;
import de.familyhub.permission.Scope;
import de.familyhub.web.ApiException;

// Wer welche Aufgaben sehen und ändern darf.
// - Sehen: alle Aufgaben mit "aufgaben/ansehen/familie", sonst nur die mir zugewiesenen.
// - Abhaken (Status ändern): mit "aufgaben/bearbeiten", eigen = mir zugewiesen.
// - Anlegen, Inhalt ändern, löschen: mit "aufgaben/erstellen" bzw. "loeschen"; eigen = mir zugewiesen und von mir
//   angelegt. Aufgaben, die mir jemand anderes zugewiesen hat, kann ich nur abhaken.
// - Punkte festlegen und Erledigung bestätigen: nur mit "punkte/freigeben/familie" (Administratoren).
@Component
public class TaskAccess {

    private final Permissions permissions;

    public TaskAccess(Permissions permissions) {
        this.permissions = permissions;
    }

    public Predicate<Task> visibilityFor(FamilyMember viewer) {
        boolean family = permissions.can(viewer, AUFGABEN, ANSEHEN, Scope.FAMILIE);
        boolean own = permissions.can(viewer, AUFGABEN, ANSEHEN, Scope.EIGEN);
        return task -> family || (own && isAssignedTo(viewer, task));
    }

    public boolean canSee(FamilyMember viewer, Task task) {
        return visibilityFor(viewer).test(task);
    }

    public void requireCreate(FamilyMember viewer, Task task) {
        Scope scope = viewer.id().equals(task.assigneeId()) ? Scope.EIGEN : Scope.FAMILIE;
        requireTaskRight(viewer, ERSTELLEN, scope, false, "Aufgaben anzulegen", "anlegen");
        requirePointsRight(viewer, task.points() > 0);
    }

    public void requireEdit(FamilyMember viewer, Task existing, Task changed) {
        requireNotConfirmed(existing);
        boolean own = isOwnTask(viewer, existing) && viewer.id().equals(changed.assigneeId());
        requireTaskRight(viewer, ERSTELLEN, own ? Scope.EIGEN : Scope.FAMILIE, isAssignedByOthers(viewer, existing),
                "Aufgaben zu ändern", "ändern");
        requirePointsRight(viewer, !existing.points().equals(changed.points()));
    }

    public void requireStatusChange(FamilyMember viewer, Task task) {
        requireNotConfirmed(task);
        Scope scope = isAssignedTo(viewer, task) ? Scope.EIGEN : Scope.FAMILIE;
        requireTaskRight(viewer, BEARBEITEN, scope, false, "Aufgaben abzuhaken", "abhaken");
    }

    public void requireDelete(FamilyMember viewer, Task task) {
        requireTaskRight(viewer, LOESCHEN, isOwnTask(viewer, task) ? Scope.EIGEN : Scope.FAMILIE,
                isAssignedByOthers(viewer, task), "Aufgaben zu löschen", "löschen");
    }

    public void requireConfirmationRight(FamilyMember viewer) {
        if (!permissions.can(viewer, PUNKTE, FREIGEBEN, Scope.FAMILIE)) {
            throw ApiException.forbidden("Nur Administratoren dürfen erledigte Aufgaben bestätigen oder zurückgeben.");
        }
    }

    private void requirePointsRight(FamilyMember viewer, boolean pointsChanged) {
        if (pointsChanged && !permissions.can(viewer, PUNKTE, FREIGEBEN, Scope.FAMILIE)) {
            throw ApiException.forbidden("Punkte für Aufgaben vergeben nur Administratoren.");
        }
    }

    private void requireTaskRight(FamilyMember viewer, Action action, Scope scope, boolean assignedByOthers,
            String what, String verb) {
        if (permissions.can(viewer, AUFGABEN, action, scope)) {
            return;
        }
        if (scope == Scope.FAMILIE && permissions.can(viewer, AUFGABEN, action, Scope.EIGEN)) {
            throw ApiException.forbidden(assignedByOthers
                    ? "Aufgaben, die dir jemand anderes zugewiesen hat, kannst du nur abhaken."
                    : "Du darfst nur deine eigenen Aufgaben " + verb + ".");
        }
        throw ApiException.forbidden("Keine Berechtigung, " + what + ".");
    }

    private static void requireNotConfirmed(Task task) {
        if (task.status() == TaskStatus.CONFIRMED) {
            throw ApiException.conflict("Die Aufgabe ist bereits bestätigt, die Punkte sind gutgeschrieben.");
        }
    }

    private static boolean isAssignedTo(FamilyMember viewer, Task task) {
        return viewer.id().equals(task.assigneeId());
    }

    private static boolean isOwnTask(FamilyMember viewer, Task task) {
        return isAssignedTo(viewer, task) && viewer.id().equals(task.createdBy());
    }

    private static boolean isAssignedByOthers(FamilyMember viewer, Task task) {
        return isAssignedTo(viewer, task) && !viewer.id().equals(task.createdBy());
    }
}
