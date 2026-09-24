package de.familyhub.settings;

import java.util.Set;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.familyhub.calendar.EventCategory;
import de.familyhub.permission.Action;
import de.familyhub.permission.Module;
import de.familyhub.permission.Permissions;
import de.familyhub.permission.RoleResolver;
import de.familyhub.permission.Scope;
import de.familyhub.security.CurrentMember;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;

@RestController
@RequestMapping("/api/settings")
@Tag(name = "Einstellungen")
public class SettingsController {

    private final FamilySettingsRepository settings;
    private final CurrentMember currentMember;
    private final Permissions permissions;
    private final RoleResolver roleResolver;

    public SettingsController(FamilySettingsRepository settings, CurrentMember currentMember,
            Permissions permissions, RoleResolver roleResolver) {
        this.settings = settings;
        this.currentMember = currentMember;
        this.permissions = permissions;
        this.roleResolver = roleResolver;
    }

    @GetMapping
    @Operation(summary = "Einstellungen der Familie")
    public SettingsResponse get() {
        currentMember.get();
        return response(settings.current());
    }

    @PutMapping
    @Operation(summary = "Einstellungen ändern", description = "Nur für Administratoren. teenAge ist nicht änderbar.")
    public SettingsResponse update(@Valid @RequestBody SettingsRequest request) {
        permissions.require(currentMember.get(), Module.SYSTEM, Action.VERWALTEN, Scope.FAMILIE,
                "Nur Administratoren dürfen die Einstellungen ändern.");
        return response(settings.save(new FamilySettings(FamilySettings.ID, request.guestCategories())));
    }

    private SettingsResponse response(FamilySettings current) {
        return new SettingsResponse(current.guestCategories(), roleResolver.teenAge());
    }

    public record SettingsRequest(
            @NotNull(message = "Kategorien für Gäste sind Pflicht (leere Liste: Gäste sehen keine Termine)")
            @Schema(description = "Kategorien, deren Termine Gäste sehen dürfen")
            Set<EventCategory> guestCategories) {
    }

    public record SettingsResponse(
            Set<EventCategory> guestCategories,
            @Schema(description = "Alter, ab dem ein Kind automatisch Jugendlicher wird "
                    + "(Konfiguration familyhub.roles.teen-age, nur lesbar)")
            int teenAge) {
    }
}
