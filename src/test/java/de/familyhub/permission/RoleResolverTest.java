package de.familyhub.permission;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.Test;

import de.familyhub.family.FamilyMember;

class RoleResolverTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 24);
    private final RoleResolver resolver = new RoleResolver(
            Clock.fixed(TODAY.atStartOfDay(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault()), 13);

    private static FamilyMember member(Role role, LocalDate birthDate, boolean roleFixed) {
        return new FamilyMember("id", "Test", "#000000", "test", null, role, birthDate, roleFixed);
    }

    @Test
    void childBecomesTeenagerOnThirteenthBirthday() {
        assertThat(resolver.effectiveRole(member(Role.KIND, TODAY.minusYears(13), false))).isEqualTo(Role.JUGENDLICHER);
        assertThat(resolver.effectiveRole(member(Role.KIND, TODAY.minusYears(13).plusDays(1), false))).isEqualTo(Role.KIND);
    }

    @Test
    void fixedRoleIsNotChangedAutomatically() {
        assertThat(resolver.effectiveRole(member(Role.KIND, TODAY.minusYears(15), true))).isEqualTo(Role.KIND);
    }

    @Test
    void otherRolesAndMissingBirthDateStayAsSet() {
        assertThat(resolver.effectiveRole(member(Role.KIND, null, false))).isEqualTo(Role.KIND);
        assertThat(resolver.effectiveRole(member(Role.GAST, TODAY.minusYears(70), false))).isEqualTo(Role.GAST);
        assertThat(resolver.effectiveRole(member(Role.ADMINISTRATOR, TODAY.minusYears(40), false)))
                .isEqualTo(Role.ADMINISTRATOR);
    }
}
