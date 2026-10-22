package de.familyhub.assistant;

import static de.familyhub.permission.Action.ANSEHEN;
import static de.familyhub.permission.Action.VERWALTEN;
import static de.familyhub.permission.Module.SPRACHASSISTENT;

import org.springframework.stereotype.Component;

import de.familyhub.family.FamilyMember;
import de.familyhub.permission.Permissions;
import de.familyhub.permission.Scope;

// Rechte am KI-Assistenten (Entscheidung vom 22.10.2026). Sie laufen über das Modul "sprachassistent" aus dem
// Rollenkonzept, denn der Mikrofon-Knopf öffnet denselben Assistenten:
// - Benutzen: "sprachassistent/ansehen/eigen", standardmäßig alle außer Gästen.
// - Einrichten und Verbindung testen: "sprachassistent/verwalten/familie", standardmäßig Administratoren.
@Component
public class AssistantAccess {

    private final Permissions permissions;

    public AssistantAccess(Permissions permissions) {
        this.permissions = permissions;
    }

    public void requireUse(FamilyMember viewer) {
        permissions.require(viewer, SPRACHASSISTENT, ANSEHEN, Scope.EIGEN, "Keine Berechtigung für den KI-Assistenten.");
    }

    public void requireManage(FamilyMember viewer) {
        permissions.require(viewer, SPRACHASSISTENT, VERWALTEN, Scope.FAMILIE,
                "Nur Administratoren dürfen den KI-Assistenten einrichten und testen.");
    }
}
