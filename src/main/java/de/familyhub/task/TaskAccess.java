package de.familyhub.task;

import static de.familyhub.permission.Action.ANSEHEN;
import static de.familyhub.permission.Action.BEARBEITEN;
import static de.familyhub.permission.Action.ERSTELLEN;
import static de.familyhub.permission.Action.FREIGEBEN;
import static de.familyhub.permission.Action.LOESCHEN;
import static de.familyhub.permission.Module.AUFGABEN;
import static de.familyhub.permission.Module.PUNKTE;

import java.util.Objects;
import java.util.function.Predicate;

import org.springframework.stereotype.Component;

import de.familyhub.family.FamilyMember;
import de.familyhub.family.FamilyMemberRepository;
import de.familyhub.permission.Action;
import de.familyhub.permission.Permissions;
import de.familyhub.permission.Role;
import de.familyhub.permission.RoleResolver;
import de.familyhub.permission.Scope;
import de.familyhub.web.ApiException;

// Wer welche Aufgaben sehen und ändern darf.
// - Sehen: alle Aufgaben mit "aufgaben/ansehen/familie", sonst nur die mir zugewiesenen.
// - Abhaken (Status ändern): mit "aufgaben/bearbeiten", eigen = mir zugewiesen.
// - Anlegen, Inhalt ändern, löschen: mit "aufgaben/erstellen" bzw. "loeschen"; eigen = mir zugewiesen und von mir
//   angelegt. Aufgaben, die mir jemand anderes zugewiesen hat, kann ich nur abhaken.
// - Punkte festlegen und Erledigung bestätigen: nur mit "punkte/freigeben/familie" (Administratoren).
// - Bonus-Aufgaben (Entscheidungen vom 29.09.2026): legen Administratoren an; offene sehen und übernehmen Kinder und
//   Jugendliche ("aufgaben/bearbeiten/eigen"), zurückgeben dürfen sie sie, solange sie nicht erledigt sind.
// - Wiederkehrende Aufgaben (Entscheidungen vom 04.11.2026): legen nur an, wer Aufgaben für die Familie anlegt
//   ("aufgaben/erstellen/familie"), denn jede neue Runde ist für alle offen. Offene Runden sehen, übernehmen und
//   abhaken alle, die eigene Aufgaben abhaken dürfen ("aufgaben/bearbeiten/eigen"), auch Erwachsene. Punkte gibt es
//   nur für Kinder und Jugendliche: Hakt ein Erwachsener eine Runde ab, verliert nur diese Runde ihre Punkte.
@Component
public class TaskAccess {

    private final Permissions permissions;
    private final RoleResolver roles;
    private final FamilyMemberRepository members;

    public TaskAccess(Permissions permissions, RoleResolver roles, FamilyMemberRepository members) {
        this.permissions = permissions;
        this.roles = roles;
        this.members = members;
    }

    public Predicate<Task> visibilityFor(FamilyMember viewer) {
        boolean family = permissions.can(viewer, AUFGABEN, ANSEHEN, Scope.FAMILIE);
        boolean own = permissions.can(viewer, AUFGABEN, ANSEHEN, Scope.EIGEN);
        boolean claims = canClaim(viewer);
        boolean takes = canTakeRounds(viewer);
        return task -> family || (own && isAssignedTo(viewer, task)) || (claims && task.isOpenBonus())
                || (takes && task.isOpenRound());
    }

    // Offene Runden wiederkehrender Aufgaben übernimmt, wer eigene Aufgaben abhaken darf.
    public boolean canTakeRounds(FamilyMember viewer) {
        return permissions.can(viewer, AUFGABEN, BEARBEITEN, Scope.EIGEN);
    }

    // Wer eine offene Runde übernimmt oder direkt abhakt
    public void requireTakeRound(FamilyMember viewer, Task task) {
        if (!canTakeRounds(viewer)) {
            throw ApiException.forbidden("Keine Berechtigung, Aufgaben zu übernehmen.");
        }
        if (!task.isOpenRound() || task.status() != TaskStatus.TODO) {
            throw ApiException.conflict("Diese Aufgabe hat schon jemand übernommen.");
        }
    }

    // Punkte gibt es nur für Kinder und Jugendliche
    public boolean collectsPoints(String memberId) {
        return isChildOrTeenager(memberId);
    }

    // Wer Punkte sammelt (Kinder und Jugendliche) und eigene Aufgaben abhaken darf, kann Bonus-Aufgaben übernehmen.
    public boolean canClaim(FamilyMember viewer) {
        Role role = roles.effectiveRole(viewer);
        return (role == Role.KIND || role == Role.JUGENDLICHER)
                && permissions.can(viewer, AUFGABEN, BEARBEITEN, Scope.EIGEN);
    }

    public void requireClaim(FamilyMember viewer, Task task) {
        if (task.repeat() != null) {
            requireTakeRound(viewer, task);
            return;
        }
        if (!canClaim(viewer)) {
            throw ApiException.forbidden("Bonus-Aufgaben übernehmen nur Kinder und Jugendliche.");
        }
        if (!task.isOpenBonus() || task.status() != TaskStatus.TODO) {
            throw ApiException.conflict("Diese Bonus-Aufgabe hat schon jemand übernommen.");
        }
    }

    public void requireRelease(FamilyMember viewer, Task task) {
        if (task.repeat() != null) {
            requireReleaseRound(viewer, task);
            return;
        }
        if (!task.bonus() || task.assigneeId() == null) {
            throw ApiException.conflict("Nur übernommene Bonus-Aufgaben lassen sich zurückgeben.");
        }
        if (!isAssignedTo(viewer, task) && !permissions.can(viewer, PUNKTE, FREIGEBEN, Scope.FAMILIE)) {
            throw ApiException.forbidden("Zurückgeben darf nur, wer die Bonus-Aufgabe übernommen hat.");
        }
        if (task.status() == TaskStatus.DONE || task.status() == TaskStatus.CONFIRMED) {
            throw ApiException.conflict("Erledigte Bonus-Aufgaben lassen sich nicht mehr zurückgeben.");
        }
    }

    // Eine Runde einer wiederkehrenden Aufgabe wieder für alle freigeben
    private void requireReleaseRound(FamilyMember viewer, Task task) {
        if (task.assigneeId() == null) {
            throw ApiException.conflict("Diese Aufgabe ist schon für alle offen.");
        }
        if (!isAssignedTo(viewer, task) && !permissions.can(viewer, AUFGABEN, ERSTELLEN, Scope.FAMILIE)) {
            throw ApiException.forbidden("Zurückgeben darf nur, wer die Aufgabe übernommen hat.");
        }
        if (task.status() == TaskStatus.DONE || task.status() == TaskStatus.CONFIRMED) {
            throw ApiException.conflict("Erledigte Aufgaben lassen sich nicht mehr zurückgeben.");
        }
    }

    public boolean canSee(FamilyMember viewer, Task task) {
        return visibilityFor(viewer).test(task);
    }

    public Task requireCreate(FamilyMember viewer, Task task) {
        if (task.bonus()) {
            requireTaskRight(viewer, ERSTELLEN, Scope.FAMILIE, false, "Bonus-Aufgaben anzulegen", "anlegen");
            requirePointsRight(viewer, true);
            return task;
        }
        Scope scope = viewer.id().equals(task.assigneeId()) ? Scope.EIGEN : Scope.FAMILIE;
        requireTaskRight(viewer, ERSTELLEN, scope, false, "Aufgaben anzulegen", "anlegen");
        requireRepeatRight(viewer, task.repeat() != null);
        requirePointsRight(viewer, task.points() > 0);
        return requireNoPointsForAdults(task);
    }

    public Task requireEdit(FamilyMember viewer, Task existing, Task changed) {
        requireNotConfirmed(existing);
        boolean own = isOwnTask(viewer, existing) && viewer.id().equals(changed.assigneeId());
        requireTaskRight(viewer, ERSTELLEN, own ? Scope.EIGEN : Scope.FAMILIE, isAssignedByOthers(viewer, existing),
                "Aufgaben zu ändern", "ändern");
        requireRepeatRight(viewer, !Objects.equals(existing.repeat(), changed.repeat()));
        requirePointsRight(viewer, !existing.points().equals(changed.points()));
        return requireNoPointsForAdults(changed);
    }

    // Jede neue Runde ist für alle offen, deshalb Aufgaben für die ganze Familie
    private void requireRepeatRight(FamilyMember viewer, boolean repeatChanged) {
        if (repeatChanged && !permissions.can(viewer, AUFGABEN, ERSTELLEN, Scope.FAMILIE)) {
            throw ApiException.forbidden("Wiederkehrende Aufgaben sind für alle offen; anlegen und ändern darf sie "
                    + "nur, wer Aufgaben für die ganze Familie anlegt.");
        }
    }

    // Erwachsene sammeln keine Sterne: Eine Aufgabe, die einem Erwachsenen (kein Kind/Jugendlicher) zugewiesen ist,
    // hat keine Punkte. Bonus-Aufgaben bleiben davon ausgenommen (sie sind nur für Kinder und Jugendliche offen),
    // wiederkehrende auch: Die Punkte gelten für die nächsten Runden, beim Abhaken verliert nur die Runde eines
    // Erwachsenen ihre Punkte (TaskController).
    // Gibt die (ggf. auf null gesetzte) Aufgabe zurück, damit der Aufrufer sie so verwenden kann.
    private Task requireNoPointsForAdults(Task task) {
        if (!task.bonus() && task.repeat() == null && task.assigneeId() != null && task.points() > 0
                && !isChildOrTeenager(task.assigneeId())) {
            return task.withoutPoints();
        }
        return task;
    }

    private boolean isChildOrTeenager(String assigneeId) {
        return members.findById(assigneeId)
                .map(member -> roles.effectiveRole(member).countsAsChild())
                .orElse(true);
    }

    public void requireStatusChange(FamilyMember viewer, Task task) {
        requireNotConfirmed(task);
        if (task.isOpenBonus()) {
            throw ApiException.conflict("Eine Bonus-Aufgabe muss erst jemand übernehmen.");
        }
        // Offene Runde: wer sie abhakt, übernimmt sie damit (TaskController)
        if (task.isOpenRound()) {
            requireTakeRound(viewer, task);
            return;
        }
        Scope scope = isAssignedTo(viewer, task) ? Scope.EIGEN : Scope.FAMILIE;
        requireTaskRight(viewer, BEARBEITEN, scope, false, "Aufgaben abzuhaken", "abhaken");
    }

    public void requireDelete(FamilyMember viewer, Task task) {
        requireTaskRight(viewer, LOESCHEN, isOwnTask(viewer, task) ? Scope.EIGEN : Scope.FAMILIE,
                isAssignedByOthers(viewer, task), "Aufgaben zu löschen", "löschen");
    }

    public void requireDeleteAll(FamilyMember viewer) {
        if (!permissions.can(viewer, AUFGABEN, LOESCHEN, Scope.FAMILIE)) {
            throw ApiException.forbidden("Nur Administratoren dürfen erledigte Aufgaben gesammelt löschen.");
        }
    }

    public boolean mayConfirm(FamilyMember viewer) {
        return permissions.can(viewer, PUNKTE, FREIGEBEN, Scope.FAMILIE);
    }

    public void requireConfirmationRight(FamilyMember viewer) {
        if (!mayConfirm(viewer)) {
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
