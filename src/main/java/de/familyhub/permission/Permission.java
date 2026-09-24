package de.familyhub.permission;

import java.util.Comparator;

import jakarta.validation.constraints.NotNull;

public record Permission(@NotNull Module module, @NotNull Action action, @NotNull Scope scope) {

    public static final Comparator<Permission> ORDER = Comparator.comparing(Permission::module)
            .thenComparing(Permission::action)
            .thenComparing(Permission::scope);

    public static Permission of(Module module, Action action, Scope scope) {
        return new Permission(module, action, scope);
    }

    public boolean covers(Module requestedModule, Action requestedAction, Scope requestedScope) {
        return module == requestedModule && action == requestedAction && scope.covers(requestedScope);
    }
}
