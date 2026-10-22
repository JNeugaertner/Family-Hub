package de.familyhub.assistant.drafts;

import java.util.List;
import java.util.Map;

import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.familyhub.assistant.AssistantAccess;
import de.familyhub.family.FamilyMember;
import de.familyhub.security.CurrentMember;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

// Entwürfe des KI-Assistenten übernehmen oder verwerfen (Phase 4). Jede Person sieht und übernimmt nur ihre eigenen.
@RestController
@RequestMapping("/api/assistant/drafts")
@Tag(name = "KI-Assistent")
public class DraftController {

    private final Drafts drafts;
    private final AssistantAccess access;
    private final CurrentMember currentMember;

    public DraftController(Drafts drafts, AssistantAccess access, CurrentMember currentMember) {
        this.drafts = drafts;
        this.access = access;
        this.currentMember = currentMember;
    }

    public record Choice(
            @NotNull(message = "index ist Pflicht")
            @Schema(description = "Position des Eintrags im Entwurf, ab 0") Integer index,
            @Schema(description = "Auf der Karte geänderte Felder, z. B. {\"quantity\": \"2 l\"}")
            Map<String, Object> changes) {
    }

    public record Acceptance(
            @NotNull(message = "items ist Pflicht")
            @Size(max = DraftTool.MAX_ENTRIES, message = "Zu viele Einträge")
            @Schema(description = "Die Einträge, die übernommen werden; alle anderen offenen werden verworfen")
            List<@Valid Choice> items) {
    }

    @PostMapping("/{id}/accept")
    @Operation(summary = "Entwurf übernehmen",
            description = "Führt die gewählten Einträge mit den Rechten der angemeldeten Person aus, wie über die "
                    + "Seiten Kalender, Aufgaben, Einkauf und Essensplan (wer dort nur vorschlagen darf, macht einen "
                    + "Vorschlag). Was nicht klappt, bleibt mit error offen; nicht gewählte Einträge werden verworfen.")
    public Draft accept(@PathVariable String id, @Valid @RequestBody Acceptance acceptance) {
        FamilyMember viewer = currentMember.get();
        access.requireUse(viewer);
        return drafts.accept(viewer, id, acceptance.items().stream()
                .map(choice -> new Drafts.Choice(choice.index(), choice.changes()))
                .toList());
    }

    @PostMapping("/{id}/discard")
    @Operation(summary = "Entwurf verwerfen", description = "Alle noch offenen Einträge; es ändert sich nichts.")
    public Draft discard(@PathVariable String id) {
        FamilyMember viewer = currentMember.get();
        access.requireUse(viewer);
        return drafts.discard(viewer, id);
    }
}
