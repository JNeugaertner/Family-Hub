package de.familyhub.security;

import org.springframework.stereotype.Component;

import de.familyhub.family.FamilyMember;
import de.familyhub.permission.Permissions;
import de.familyhub.permission.RoleResolver;

@Component
public class MeResponses {

    private final RoleResolver roleResolver;
    private final Permissions permissions;

    public MeResponses(RoleResolver roleResolver, Permissions permissions) {
        this.roleResolver = roleResolver;
        this.permissions = permissions;
    }

    public MeResponse of(FamilyMember member) {
        return new MeResponse(member.id(), member.name(), member.color(), member.username(), member.role(),
                roleResolver.effectiveRole(member), member.birthDate(), permissions.effectiveSorted(member));
    }
}
