package de.familyhub.waste;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import de.familyhub.family.FamilyMember;
import de.familyhub.permission.Action;
import de.familyhub.permission.Module;
import de.familyhub.permission.Permissions;
import de.familyhub.permission.Scope;
import de.familyhub.security.CurrentMember;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/waste")
@Tag(name = "Müllabfuhr")
public class WasteCollectionController {

    private final WasteCollectionService collections;
    private final CurrentMember currentMember;
    private final Permissions permissions;

    public WasteCollectionController(WasteCollectionService collections, CurrentMember currentMember,
            Permissions permissions) {
        this.collections = collections;
        this.currentMember = currentMember;
        this.permissions = permissions;
    }

    @GetMapping
    @Operation(summary = "Müllabfuhrtermine der Familie")
    public WasteResponse get() {
        currentMember.get();
        WasteCollection current = collections.current();
        return current == null ? new WasteResponse(null, null, 0, null, List.of())
                : new WasteResponse(current.fileName(), current.uploadedAt(), current.points(), current.assigneeId(),
                        current.pickups());
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(summary = "Müllabfuhr-ICS importieren", description = "Nur für Administratoren.")
    public WasteResponse upload(@RequestParam("file") MultipartFile file, @RequestParam int points,
            @RequestParam(required = false) String assigneeId) {
        FamilyMember viewer = currentMember.get();
        permissions.require(viewer, Module.SYSTEM, Action.VERWALTEN, Scope.FAMILIE,
                "Nur Administratoren dürfen die Einstellungen ändern.");
        WasteCollection imported = collections.importCalendar(file, points, assigneeId, viewer);
        return new WasteResponse(imported.fileName(), imported.uploadedAt(), imported.points(),
                imported.assigneeId(), imported.pickups());
    }

    public record WasteResponse(String fileName, LocalDateTime uploadedAt, int points, String assigneeId,
            List<WasteCollection.Pickup> pickups) {
    }
}