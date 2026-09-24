package de.familyhub.family;

import java.util.Collection;
import java.util.List;

import org.springframework.stereotype.Component;

import de.familyhub.permission.Action;
import de.familyhub.permission.Module;
import de.familyhub.permission.Permission;
import de.familyhub.permission.Permissions;
import de.familyhub.permission.RoleResolver;
import de.familyhub.permission.Scope;

@Component
public class MemberResponses {

    private final RoleResolver roleResolver;
    private final Permissions permissions;

    public MemberResponses(RoleResolver roleResolver, Permissions permissions) {
        this.roleResolver = roleResolver;
        this.permissions = permissions;
    }

    // Benutzername und Geburtsdatum sieht, wer Mitglieder verwalten darf, oder die Person selbst.
    // Einzelrechte sieht nur, wer Rollen und Rechte verwalten darf.
    public MemberResponse of(FamilyMember member, FamilyMember viewer) {
        boolean manager = permissions.can(viewer, Module.FAMILIE, Action.VERWALTEN, Scope.FAMILIE);
        boolean details = manager || member.id().equals(viewer.id());
        boolean rights = permissions.can(viewer, Module.SYSTEM, Action.VERWALTEN, Scope.FAMILIE);
        return new MemberResponse(member.id(), member.name(), member.color(),
                details ? member.username() : null,
                member.role(), roleResolver.effectiveRole(member),
                details ? member.birthDate() : null,
                member.roleFixed(),
                rights ? sorted(member.extraPermissions()) : null,
                rights ? sorted(member.revokedPermissions()) : null);
    }

    private static List<Permission> sorted(Collection<Permission> permissions) {
        return permissions.stream().sorted(Permission.ORDER).toList();
    }
}
