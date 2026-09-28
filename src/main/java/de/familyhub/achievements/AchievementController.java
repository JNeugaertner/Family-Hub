package de.familyhub.achievements;

import static de.familyhub.permission.Action.ANSEHEN;
import static de.familyhub.permission.Action.VERWALTEN;
import static de.familyhub.permission.Module.PUNKTE;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import de.familyhub.family.FamilyMember;
import de.familyhub.family.FamilyMemberRepository;
import de.familyhub.permission.Permissions;
import de.familyhub.permission.Scope;
import de.familyhub.security.CurrentMember;
import de.familyhub.web.ApiException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

// Erfolge ansehen wie Punkte ("punkte/ansehen": Familie alle Kinder, sonst nur sich selbst); den Katalog
// anpassen dürfen Administratoren ("punkte/verwalten/familie").
@RestController
@RequestMapping("/api/achievements")
@Tag(name = "Erfolge")
public class AchievementController {

    private final AchievementRepository achievements;
    private final EarnedAchievementRepository earned;
    private final AchievementService service;
    private final FamilyMemberRepository members;
    private final CurrentMember currentMember;
    private final Permissions permissions;

    public AchievementController(AchievementRepository achievements, EarnedAchievementRepository earned,
            AchievementService service, FamilyMemberRepository members, CurrentMember currentMember,
            Permissions permissions) {
        this.achievements = achievements;
        this.earned = earned;
        this.service = service;
        this.members = members;
        this.currentMember = currentMember;
        this.permissions = permissions;
    }

    public record Settings(
            @NotNull(message = "Aktiv ist Pflicht") Boolean active,
            @NotNull(message = "Ziel ist Pflicht") @Min(value = 1, message = "Das Ziel muss mindestens 1 sein")
            @Max(value = 10000, message = "Höchstens 10000") Integer target,
            @NotNull(message = "Bonus ist Pflicht") @Min(value = 0, message = "Der Bonus darf nicht negativ sein")
            @Max(value = 1000, message = "Höchstens 1000 Bonuspunkte") Integer bonus) {
    }

    // Fortschritt eines Kindes je Erfolg; current ist höchstens das Ziel.
    public record Item(String achievementId, String icon, String name, String description, int current, int target,
            int bonus, LocalDateTime earnedAt) {
    }

    public record MemberAchievements(String memberId, List<Item> items) {
    }

    @GetMapping
    @Operation(summary = "Erfolge-Katalog", description = "Deaktivierte Erfolge nur für Administratoren.")
    public List<Achievement> list() {
        FamilyMember viewer = currentMember.get();
        requireView(viewer);
        boolean all = permissions.can(viewer, PUNKTE, VERWALTEN, Scope.FAMILIE);
        return achievements.findAll().stream().filter(a -> all || a.active()).toList();
    }

    @GetMapping("/progress")
    @Operation(summary = "Erfolge und Fortschritt je Kind",
            description = "Mit Familienrecht alle Kinder und Jugendlichen, sonst nur die eigenen. Enthält aktive Erfolge "
                    + "und bereits erreichte.")
    public List<MemberAchievements> progress() {
        FamilyMember viewer = currentMember.get();
        requireView(viewer);
        Stream<FamilyMember> visible = permissions.can(viewer, PUNKTE, ANSEHEN, Scope.FAMILIE)
                ? members.findAll().stream() : Stream.of(viewer);
        List<Achievement> catalog = achievements.findAll();
        return visible.filter(service::collects).map(m -> progressOf(m, catalog)).toList();
    }

    @PutMapping("/{id}")
    @Operation(summary = "Erfolg anpassen",
            description = "Nur für Administratoren: aktiv, Ziel und Bonus. Wer das neue Ziel schon erreicht, bekommt "
                    + "den Erfolg sofort.")
    public Achievement update(@PathVariable String id, @Valid @RequestBody Settings settings) {
        FamilyMember viewer = currentMember.get();
        permissions.require(viewer, PUNKTE, VERWALTEN, Scope.FAMILIE, "Nur Administratoren dürfen Erfolge anpassen.");
        Achievement existing = achievements.findById(id)
                .orElseThrow(() -> ApiException.notFound("Erfolg " + id + " existiert nicht."));
        Achievement saved = achievements.save(existing.withSettings(settings.active(), settings.target(),
                settings.bonus()));
        service.recheck(saved, viewer);
        return saved;
    }

    private MemberAchievements progressOf(FamilyMember member, List<Achievement> catalog) {
        AchievementProgress current = service.progressOf(member.id());
        Map<String, EarnedAchievement> earnedById = earned.findByMemberIdOrderByEarnedAtDesc(member.id()).stream()
                .collect(Collectors.toMap(EarnedAchievement::achievementId, Function.identity(), (a, b) -> a));
        List<Item> items = catalog.stream()
                .filter(a -> a.active() || earnedById.containsKey(a.id()))
                .map(a -> {
                    EarnedAchievement e = earnedById.get(a.id());
                    return new Item(a.id(), a.icon(), a.name(), a.description(),
                            Math.min(current.valueFor(a), a.target()), a.target(), e == null ? a.bonus() : e.bonus(),
                            e == null ? null : e.earnedAt());
                })
                .toList();
        return new MemberAchievements(member.id(), items);
    }

    private void requireView(FamilyMember viewer) {
        permissions.require(viewer, PUNKTE, ANSEHEN, Scope.EIGEN, "Keine Berechtigung, Erfolge anzusehen.");
    }
}
