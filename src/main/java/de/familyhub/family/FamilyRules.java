package de.familyhub.family;

import java.util.List;

import org.springframework.stereotype.Component;

import de.familyhub.permission.Role;
import de.familyhub.web.ApiException;

// Familiengrenzen aus User Story A.1 (Entscheidung vom 24.09.2026): höchstens 2 Administratoren und
// 5 Kinder, Jugendliche zählen als Kinder, Gäste zählen nicht mit. Gezählt wird die gesetzte Rolle.
@Component
public class FamilyRules {

    static final int MAX_ADMINISTRATORS = 2;
    static final int MAX_CHILDREN = 5;

    private final FamilyMemberRepository members;

    public FamilyRules(FamilyMemberRepository members) {
        this.members = members;
    }

    // otherThanId: beim Ändern das Mitglied selbst nicht mitzählen; beim Anlegen null.
    public void checkLimits(Role role, String otherThanId) {
        List<FamilyMember> others = othersThan(otherThanId);
        if (role == Role.ADMINISTRATOR
                && others.stream().filter(m -> m.role() == Role.ADMINISTRATOR).count() >= MAX_ADMINISTRATORS) {
            throw ApiException.invalidField("role", "Es gibt bereits " + MAX_ADMINISTRATORS + " Administratoren");
        }
        if (role.countsAsChild()
                && others.stream().filter(m -> m.role().countsAsChild()).count() >= MAX_CHILDREN) {
            throw ApiException.invalidField("role", "Es gibt bereits " + MAX_CHILDREN + " Kinder oder Jugendliche");
        }
    }

    // newRole null bedeutet: Mitglied wird gelöscht.
    public void checkAdministratorRemains(FamilyMember member, Role newRole) {
        if (member.role() == Role.ADMINISTRATOR && newRole != Role.ADMINISTRATOR
                && othersThan(member.id()).stream().noneMatch(m -> m.role() == Role.ADMINISTRATOR)) {
            throw ApiException.conflict("Es muss mindestens ein Administrator in der Familie bleiben.");
        }
    }

    private List<FamilyMember> othersThan(String id) {
        return members.findAll().stream().filter(m -> !m.id().equals(id)).toList();
    }
}
