package de.familyhub.family;

import org.springframework.stereotype.Component;

import de.familyhub.permission.Role;
import de.familyhub.permission.RoleResolver;

@Component
public class MemberResponses {

    private final RoleResolver roleResolver;

    public MemberResponses(RoleResolver roleResolver) {
        this.roleResolver = roleResolver;
    }

    // Benutzername und Geburtsdatum sieht nur, wer Mitglieder verwalten darf, oder die Person selbst.
    public MemberResponse of(FamilyMember member, FamilyMember viewer) {
        boolean details = member.id().equals(viewer.id()) || isAdmin(viewer);
        return new MemberResponse(member.id(), member.name(), member.color(),
                details ? member.username() : null,
                member.role(), roleResolver.effectiveRole(member),
                details ? member.birthDate() : null,
                member.roleFixed());
    }

    public boolean isAdmin(FamilyMember member) {
        return roleResolver.effectiveRole(member) == Role.ADMINISTRATOR;
    }
}
