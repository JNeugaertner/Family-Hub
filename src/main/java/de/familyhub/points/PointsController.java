package de.familyhub.points;

import static de.familyhub.permission.Action.ANSEHEN;
import static de.familyhub.permission.Module.PUNKTE;

import java.util.List;
import java.util.Map;
import java.util.stream.Stream;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import de.familyhub.family.FamilyMember;
import de.familyhub.family.FamilyMemberRepository;
import de.familyhub.permission.Permissions;
import de.familyhub.permission.Scope;
import de.familyhub.security.CurrentMember;
import de.familyhub.web.ApiException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

// Punktestände und Historie. Mit "punkte/ansehen/familie" für alle Mitglieder, mit "eigen" nur der eigene Stand.
// Punkte entstehen, wenn ein Administrator eine erledigte Aufgabe bestätigt (POST /api/tasks/{id}/confirm).
@RestController
@RequestMapping("/api/points")
@Tag(name = "Punkte")
public class PointsController {

    private final PointsService points;
    private final FamilyMemberRepository members;
    private final CurrentMember currentMember;
    private final Permissions permissions;

    public PointsController(PointsService points, FamilyMemberRepository members, CurrentMember currentMember,
            Permissions permissions) {
        this.points = points;
        this.members = members;
        this.currentMember = currentMember;
        this.permissions = permissions;
    }

    public record Balance(String memberId, int points) {
    }

    @GetMapping
    @Operation(summary = "Punktestände", description = "Mit Familienrecht alle Mitglieder, sonst nur der eigene Stand.")
    public List<Balance> balances() {
        FamilyMember viewer = currentMember.get();
        boolean family = requireView(viewer);
        Map<String, Integer> sums = points.balances();
        Stream<FamilyMember> visible = family ? members.findAll().stream() : Stream.of(viewer);
        return visible.map(m -> new Balance(m.id(), sums.getOrDefault(m.id(), 0))).toList();
    }

    @GetMapping("/history")
    @Operation(summary = "Punkte-Historie", description = "Buchungen, neueste zuerst. Ohne memberId alle sichtbaren.")
    public List<PointEntry> history(
            @Parameter(description = "Nur Buchungen dieses Familienmitglieds")
            @RequestParam(required = false) String memberId) {
        FamilyMember viewer = currentMember.get();
        if (requireView(viewer)) {
            return points.history(memberId);
        }
        if (memberId != null && !memberId.equals(viewer.id())) {
            throw ApiException.forbidden("Du darfst nur deine eigenen Punkte sehen.");
        }
        return points.history(viewer.id());
    }

    // true: darf alle Punktestände sehen; false: nur die eigenen
    private boolean requireView(FamilyMember viewer) {
        if (permissions.can(viewer, PUNKTE, ANSEHEN, Scope.FAMILIE)) {
            return true;
        }
        permissions.require(viewer, PUNKTE, ANSEHEN, Scope.EIGEN, "Keine Berechtigung, Punkte anzusehen.");
        return false;
    }
}
