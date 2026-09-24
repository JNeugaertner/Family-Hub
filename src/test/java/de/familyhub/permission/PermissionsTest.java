package de.familyhub.permission;

import static de.familyhub.permission.Action.ANSEHEN;
import static de.familyhub.permission.Action.ERSTELLEN;
import static de.familyhub.permission.Action.FREIGEBEN;
import static de.familyhub.permission.Action.VERWALTEN;
import static de.familyhub.permission.Action.VORSCHLAGEN;
import static de.familyhub.permission.Module.EINKAUF;
import static de.familyhub.permission.Module.FAMILIE;
import static de.familyhub.permission.Module.KALENDER;
import static de.familyhub.permission.Module.PUNKTE;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.LocalDate;
import java.util.Set;

import org.junit.jupiter.api.Test;

import de.familyhub.family.FamilyMember;

class PermissionsTest {

    private final Permissions permissions = new Permissions(new RoleResolver(Clock.systemDefaultZone(), 13));

    private static FamilyMember member(Role role, Set<Permission> extra, Set<Permission> revoked) {
        return new FamilyMember("id", "Test", "#000000", "test", null, role, null, false, extra, revoked);
    }

    private static FamilyMember member(Role role) {
        return member(role, Set.of(), Set.of());
    }

    @Test
    void administratorMayDoEverything() {
        FamilyMember admin = member(Role.ADMINISTRATOR);
        for (Module module : Module.values()) {
            for (Action action : Action.values()) {
                assertThat(permissions.can(admin, module, action, Scope.FAMILIE)).isTrue();
            }
        }
    }

    @Test
    void childSeesFamilyCalendarButCannotCreateOrDecide() {
        FamilyMember child = member(Role.KIND);
        assertThat(permissions.can(child, KALENDER, ANSEHEN, Scope.FAMILIE)).isTrue();
        assertThat(permissions.can(child, KALENDER, ERSTELLEN, Scope.EIGEN)).isFalse();
        assertThat(permissions.can(child, KALENDER, FREIGEBEN, Scope.FAMILIE)).isFalse();
        assertThat(permissions.can(child, PUNKTE, ANSEHEN, Scope.EIGEN)).isTrue();
        assertThat(permissions.can(child, PUNKTE, ANSEHEN, Scope.FAMILIE)).isFalse();
    }

    @Test
    void teenagerCreatesOwnEventsAndProposesOthers() {
        FamilyMember teen = member(Role.JUGENDLICHER);
        assertThat(permissions.can(teen, KALENDER, ERSTELLEN, Scope.EIGEN)).isTrue();
        assertThat(permissions.can(teen, KALENDER, ERSTELLEN, Scope.FAMILIE)).isFalse();
        assertThat(permissions.can(teen, KALENDER, VORSCHLAGEN, Scope.FAMILIE)).isTrue();
        assertThat(permissions.can(teen, FAMILIE, VERWALTEN, Scope.FAMILIE)).isFalse();
    }

    @Test
    void guestOnlySeesReleasedData() {
        FamilyMember guest = member(Role.GAST);
        assertThat(permissions.can(guest, KALENDER, ANSEHEN, Scope.FREIGEGEBEN)).isTrue();
        assertThat(permissions.can(guest, KALENDER, ANSEHEN, Scope.FAMILIE)).isFalse();
        assertThat(permissions.can(guest, PUNKTE, ANSEHEN, Scope.EIGEN)).isFalse();
    }

    @Test
    void broaderScopeCoversNarrowerScope() {
        FamilyMember child = member(Role.KIND);
        assertThat(permissions.can(child, KALENDER, ANSEHEN, Scope.EIGEN)).isTrue();
        assertThat(permissions.can(child, KALENDER, ANSEHEN, Scope.FREIGEGEBEN)).isTrue();
    }

    @Test
    void administratorCanGrantAndRevokeSinglePermissions() {
        Permission shopping = Permission.of(EINKAUF, Action.BEARBEITEN, Scope.FAMILIE);
        Permission calendar = Permission.of(KALENDER, ANSEHEN, Scope.FAMILIE);
        FamilyMember child = member(Role.KIND, Set.of(shopping), Set.of(calendar));

        assertThat(permissions.can(child, EINKAUF, Action.BEARBEITEN, Scope.FAMILIE)).isTrue();
        assertThat(permissions.can(child, KALENDER, ANSEHEN, Scope.FAMILIE)).isFalse();
    }

    @Test
    void singlePermissionsDoNotApplyToAdministrators() {
        Permission calendar = Permission.of(KALENDER, ANSEHEN, Scope.FAMILIE);
        FamilyMember admin = member(Role.ADMINISTRATOR, Set.of(), Set.of(calendar));

        assertThat(permissions.can(admin, KALENDER, ANSEHEN, Scope.FAMILIE)).isTrue();
    }

    @Test
    void permissionsFollowTheEffectiveRole() {
        FamilyMember teenByAge = new FamilyMember("id", "Emma", "#000000", "emma", null, Role.KIND,
                LocalDate.now().minusYears(14), false);
        assertThat(permissions.can(teenByAge, KALENDER, ERSTELLEN, Scope.EIGEN)).isTrue();
    }
}
