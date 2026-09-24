package de.familyhub.permission;

import java.util.Arrays;
import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.familyhub.security.CurrentMember;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/roles")
@Tag(name = "Rollen")
public class RoleController {

    private final CurrentMember currentMember;

    public RoleController(CurrentMember currentMember) {
        this.currentMember = currentMember;
    }

    @GetMapping
    @Operation(summary = "Rollen mit ihren Standardrechten",
            description = "Grundlage für die Anzeige von Rollen und Einzelrechten im Frontend.")
    public List<RoleResponse> list() {
        currentMember.get();
        return Arrays.stream(Role.values())
                .map(role -> new RoleResponse(role, role.displayName(), role != Role.KI_AGENT,
                        StandardRoles.permissionsOf(role).stream().sorted(Permission.ORDER).toList()))
                .toList();
    }

    public record RoleResponse(Role id, String name, boolean assignable, List<Permission> permissions) {
    }
}
