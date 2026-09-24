package de.familyhub.permission;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;

import de.familyhub.family.FamilyMember;
import de.familyhub.web.ApiException;

// Geltende Rechte = Standardrechte der Rolle + zusätzliche Einzelrechte - entzogene Einzelrechte.
// Für Administratoren gelten keine Einzelrechte, damit sich niemand versehentlich aussperrt.
@Component
public class Permissions {

    private final RoleResolver roleResolver;

    public Permissions(RoleResolver roleResolver) {
        this.roleResolver = roleResolver;
    }

    public Set<Permission> effective(FamilyMember member) {
        Role role = roleResolver.effectiveRole(member);
        if (role == Role.ADMINISTRATOR) {
            return StandardRoles.permissionsOf(role);
        }
        Set<Permission> result = new HashSet<>(StandardRoles.permissionsOf(role));
        result.addAll(member.extraPermissions());
        result.removeAll(member.revokedPermissions());
        return result;
    }

    public List<Permission> effectiveSorted(FamilyMember member) {
        return effective(member).stream().sorted(Permission.ORDER).toList();
    }

    public boolean can(FamilyMember member, Module module, Action action, Scope scope) {
        return effective(member).stream().anyMatch(p -> p.covers(module, action, scope));
    }

    public void require(FamilyMember member, Module module, Action action, Scope scope, String deniedMessage) {
        if (!can(member, module, action, scope)) {
            throw ApiException.forbidden(deniedMessage);
        }
    }
}
