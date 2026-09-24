package de.familyhub.permission;

import java.time.Clock;
import java.time.LocalDate;
import java.time.Period;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import de.familyhub.family.FamilyMember;

// Altersübergang Kind -> Jugendlicher (Entscheidung vom 22.09.2026): automatisch ab dem konfigurierten Alter,
// außer ein Administrator hat die Rolle festgesetzt (roleFixed).
@Component
public class RoleResolver {

    private final Clock clock;
    private final int teenAge;

    public RoleResolver(Clock clock, @Value("${familyhub.roles.teen-age:13}") int teenAge) {
        this.clock = clock;
        this.teenAge = teenAge;
    }

    public int teenAge() {
        return teenAge;
    }

    public Role effectiveRole(FamilyMember member) {
        if (member.role() == Role.KIND && !member.roleFixed() && member.birthDate() != null
                && Period.between(member.birthDate(), LocalDate.now(clock)).getYears() >= teenAge) {
            return Role.JUGENDLICHER;
        }
        return member.role();
    }
}
