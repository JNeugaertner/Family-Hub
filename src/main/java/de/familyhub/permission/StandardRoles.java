package de.familyhub.permission;

import static de.familyhub.permission.Action.ANSEHEN;
import static de.familyhub.permission.Action.BEARBEITEN;
import static de.familyhub.permission.Action.ERSTELLEN;
import static de.familyhub.permission.Action.LOESCHEN;
import static de.familyhub.permission.Action.VORSCHLAGEN;
import static de.familyhub.permission.Module.AUFGABEN;
import static de.familyhub.permission.Module.EINKAUF;
import static de.familyhub.permission.Module.ESSEN;
import static de.familyhub.permission.Module.FAHRZEIT;
import static de.familyhub.permission.Module.FAMILIE;
import static de.familyhub.permission.Module.KALENDER;
import static de.familyhub.permission.Module.MUELL;
import static de.familyhub.permission.Module.PUNKTE;
import static de.familyhub.permission.Module.SPRACHASSISTENT;
import static de.familyhub.permission.Module.SYSTEM;
import static de.familyhub.permission.Module.WETTER;
import static de.familyhub.permission.Permission.of;

import java.util.Arrays;
import java.util.EnumMap;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

// Standardrechte je Rolle nach Rollenkonzept.docx und docs/konzept/rechtekonzept.drawio.
// Entscheidungen vom 24.09.2026: Kinder sehen alle nicht privaten Termine, Gäste nur freigegebene
// Kategorien; Jugendliche verwalten eigene Termine und schlagen Termine für andere vor.
public final class StandardRoles {

    private static final Map<Role, Set<Permission>> PERMISSIONS = new EnumMap<>(Role.class);

    static {
        PERMISSIONS.put(Role.ADMINISTRATOR, Arrays.stream(Module.values())
                .flatMap(module -> Arrays.stream(Action.values()).map(action -> of(module, action, Scope.FAMILIE)))
                .collect(Collectors.toUnmodifiableSet()));

        PERMISSIONS.put(Role.JUGENDLICHER, Set.of(
                of(FAMILIE, ANSEHEN, Scope.FAMILIE),
                of(KALENDER, ANSEHEN, Scope.FAMILIE),
                of(KALENDER, ERSTELLEN, Scope.EIGEN),
                of(KALENDER, BEARBEITEN, Scope.EIGEN),
                of(KALENDER, LOESCHEN, Scope.EIGEN),
                of(KALENDER, VORSCHLAGEN, Scope.FAMILIE),
                of(AUFGABEN, ANSEHEN, Scope.FAMILIE),
                of(AUFGABEN, ERSTELLEN, Scope.EIGEN),
                of(AUFGABEN, BEARBEITEN, Scope.EIGEN),
                of(AUFGABEN, LOESCHEN, Scope.EIGEN),
                of(PUNKTE, ANSEHEN, Scope.FAMILIE),
                of(EINKAUF, ANSEHEN, Scope.FAMILIE),
                of(EINKAUF, ERSTELLEN, Scope.FAMILIE),
                of(EINKAUF, BEARBEITEN, Scope.FAMILIE),
                of(EINKAUF, LOESCHEN, Scope.FAMILIE),
                of(ESSEN, ANSEHEN, Scope.FAMILIE),
                of(ESSEN, BEARBEITEN, Scope.FAMILIE),
                of(ESSEN, VORSCHLAGEN, Scope.FAMILIE),
                of(WETTER, ANSEHEN, Scope.FAMILIE),
                of(MUELL, ANSEHEN, Scope.FAMILIE),
                of(FAHRZEIT, ANSEHEN, Scope.FAMILIE),
                of(SPRACHASSISTENT, ANSEHEN, Scope.EIGEN),
                of(SYSTEM, BEARBEITEN, Scope.EIGEN)));

        PERMISSIONS.put(Role.KIND, Set.of(
                of(FAMILIE, ANSEHEN, Scope.FAMILIE),
                of(KALENDER, ANSEHEN, Scope.FAMILIE),
                of(AUFGABEN, ANSEHEN, Scope.EIGEN),
                of(AUFGABEN, BEARBEITEN, Scope.EIGEN),
                of(PUNKTE, ANSEHEN, Scope.EIGEN),
                of(EINKAUF, VORSCHLAGEN, Scope.FAMILIE),
                of(ESSEN, VORSCHLAGEN, Scope.FAMILIE),
                of(WETTER, ANSEHEN, Scope.FAMILIE),
                of(MUELL, ANSEHEN, Scope.FAMILIE),
                of(SPRACHASSISTENT, ANSEHEN, Scope.EIGEN)));

        PERMISSIONS.put(Role.GAST, Set.of(
                of(FAMILIE, ANSEHEN, Scope.FREIGEGEBEN),
                of(KALENDER, ANSEHEN, Scope.FREIGEGEBEN),
                of(AUFGABEN, ANSEHEN, Scope.FREIGEGEBEN),
                of(WETTER, ANSEHEN, Scope.FREIGEGEBEN),
                of(MUELL, ANSEHEN, Scope.FREIGEGEBEN)));

        // Informiert und schlägt vor, entscheidet und ändert nichts selbst.
        PERMISSIONS.put(Role.KI_AGENT, Set.of(
                of(FAMILIE, ANSEHEN, Scope.FAMILIE),
                of(KALENDER, ANSEHEN, Scope.FAMILIE),
                of(KALENDER, VORSCHLAGEN, Scope.FAMILIE),
                of(AUFGABEN, ANSEHEN, Scope.FAMILIE),
                of(AUFGABEN, VORSCHLAGEN, Scope.FAMILIE),
                of(EINKAUF, VORSCHLAGEN, Scope.FAMILIE),
                of(ESSEN, VORSCHLAGEN, Scope.FAMILIE),
                of(WETTER, ANSEHEN, Scope.FAMILIE),
                of(MUELL, ANSEHEN, Scope.FAMILIE),
                of(FAHRZEIT, ANSEHEN, Scope.FAMILIE),
                of(SPRACHASSISTENT, ANSEHEN, Scope.FAMILIE)));
    }

    private StandardRoles() {
    }

    public static Set<Permission> permissionsOf(Role role) {
        return PERMISSIONS.get(role);
    }
}
