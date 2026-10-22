package de.familyhub.assistant.tools;

import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import de.familyhub.family.FamilyMember;
import de.familyhub.permission.Action;
import de.familyhub.permission.Module;
import de.familyhub.permission.Permission;
import de.familyhub.permission.Permissions;
import de.familyhub.permission.Role;
import de.familyhub.permission.Scope;
import de.familyhub.permission.StandardRoles;

// Rechte-Schnittmenge (Entscheidung vom 22.10.2026): Ein Bereich steht dem Assistenten nur offen, wenn die Rolle
// KI-Agent ihn lesen darf UND die fragende Person ihn selbst sehen darf. Was die Person darin sieht, filtern die
// Werkzeuge danach mit denselben Regeln wie die Oberfläche (CalendarAccess, TaskAccess, ...).
@Component
public class AgentRights {

    private static final Set<Permission> AGENT = StandardRoles.permissionsOf(Role.KI_AGENT);
    private static final List<Action> WRITING = List.of(Action.ERSTELLEN, Action.BEARBEITEN, Action.LOESCHEN,
            Action.VORSCHLAGEN);

    private final Permissions permissions;

    public AgentRights(Permissions permissions) {
        this.permissions = permissions;
    }

    // viewerScope: welcher Bereich der Person mindestens nötig ist, z. B. "eigen" für eigene Termine
    public boolean mayRead(FamilyMember viewer, Module module, Scope viewerScope) {
        return AGENT.stream().anyMatch(p -> p.covers(module, Action.ANSEHEN, Scope.FAMILIE))
                && permissions.can(viewer, module, Action.ANSEHEN, viewerScope);
    }

    // Entwürfe (Phase 4): Die Rolle KI-Agent darf im Bereich vorschlagen UND die Person sieht den Bereich und kann
    // dort selbst etwas anlegen, ändern, löschen oder vorschlagen. Ob der einzelne Entwurf erlaubt ist, prüfen die
    // Regeln des Bereichs.
    public boolean mayDraft(FamilyMember viewer, Module module) {
        return AGENT.stream().anyMatch(p -> p.covers(module, Action.VORSCHLAGEN, Scope.FAMILIE))
                && permissions.can(viewer, module, Action.ANSEHEN, Scope.EIGEN)
                && WRITING.stream().anyMatch(action -> permissions.can(viewer, module, action, Scope.EIGEN));
    }
}
