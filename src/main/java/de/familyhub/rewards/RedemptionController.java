package de.familyhub.rewards;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import de.familyhub.family.FamilyMember;
import de.familyhub.family.FamilyMemberRepository;
import de.familyhub.points.PointsService;
import de.familyhub.security.CurrentMember;
import de.familyhub.web.ApiException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Kinder und Jugendliche lösen Belohnungen für sich selbst ein. Die Punkte werden sofort abgezogen; Eltern
// genehmigen oder lehnen ab. Beim Ablehnen und Zurückziehen einer offenen Einlösung kommen sie zurück.
@RestController
@RequestMapping("/api/redemptions")
@Tag(name = "Belohnungen")
public class RedemptionController {

    private final RedemptionRepository redemptions;
    private final RewardRepository rewards;
    private final FamilyMemberRepository members;
    private final PointsService points;
    private final CurrentMember currentMember;
    private final RewardAccess access;
    private final Clock clock;

    public RedemptionController(RedemptionRepository redemptions, RewardRepository rewards,
            FamilyMemberRepository members, PointsService points, CurrentMember currentMember, RewardAccess access,
            Clock clock) {
        this.redemptions = redemptions;
        this.rewards = rewards;
        this.members = members;
        this.points = points;
        this.currentMember = currentMember;
        this.access = access;
        this.clock = clock;
    }

    public record RedeemRequest(
            @NotBlank(message = "Belohnung fehlt") String rewardId,
            @Schema(description = "Familienmitglied (muss der angemeldeten Person entsprechen)")
            String memberId) {
    }

    public record RejectRequest(
            @Size(max = 200, message = "Der Grund darf höchstens 200 Zeichen lang sein") String reason) {
    }

    @GetMapping
    @Operation(summary = "Einlösungen", description = "Neueste zuerst. Mit Familienrecht alle, sonst nur die eigenen.")
    public List<Redemption> list(
            @Parameter(description = "Nur Einlösungen dieses Familienmitglieds")
            @RequestParam(required = false) String memberId,
            @Parameter(description = "Nur mit diesem Status, z. B. pending für offene")
            @RequestParam(required = false) RedemptionStatus status) {
        FamilyMember viewer = currentMember.get();
        access.requireView(viewer);
        List<Redemption> result;
        if (access.seesFamily(viewer)) {
            result = memberId == null ? redemptions.findAllByOrderByRequestedAtDesc()
                    : redemptions.findByMemberIdOrderByRequestedAtDesc(memberId);
        } else {
            if (memberId != null && !memberId.equals(viewer.id())) {
                throw ApiException.forbidden("Du darfst nur deine eigenen Einlösungen sehen.");
            }
            result = redemptions.findByMemberIdOrderByRequestedAtDesc(viewer.id());
        }
        return result.stream().filter(r -> status == null || r.status() == status).toList();
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @Operation(summary = "Belohnung einlösen",
            description = "Kinder und Jugendliche lösen für sich selbst ein. Die Punkte werden sofort abgezogen "
                    + "und die Einlösung wartet auf die Eltern.")
    public Redemption redeem(@Valid @RequestBody RedeemRequest request) {
        FamilyMember viewer = currentMember.get();
        String memberId = request.memberId() == null ? viewer.id() : request.memberId();
        access.requireRedeem(viewer, memberId);
        if (!members.existsById(memberId)) {
            throw ApiException.invalidField("memberId", "Familienmitglied existiert nicht");
        }
        Reward reward = rewards.findById(request.rewardId())
                .filter(Reward::active)
                .orElseThrow(() -> ApiException.notFound("Diese Belohnung gibt es nicht (mehr)."));
        requireRedeemable(reward, memberId);
        int balance = points.balance(memberId);
        if (balance < reward.cost()) {
            throw ApiException.conflict("Nicht genug Punkte: " + balance + " von " + reward.cost() + " vorhanden.");
        }

        LocalDateTime now = LocalDateTime.now(clock);
        boolean decided = access.mayDecide(viewer);
        points.book(memberId, -reward.cost(), "Belohnung eingelöst: " + reward.name(), viewer.id());
        return redemptions.save(new Redemption(null, reward.id(), reward.name(), reward.emoji(), reward.cost(),
                memberId, decided ? RedemptionStatus.APPROVED : RedemptionStatus.PENDING, now, viewer.id(),
                decided ? now : null, decided ? viewer.id() : null, null));
    }

    @PostMapping("/{id}/approve")
    @Operation(summary = "Einlösung genehmigen", description = "Nur für Administratoren.")
    public Redemption approve(@PathVariable String id) {
        FamilyMember viewer = currentMember.get();
        access.requireDecide(viewer);
        Redemption redemption = findPending(id, viewer);
        return redemptions.save(redemption.decided(RedemptionStatus.APPROVED, LocalDateTime.now(clock), viewer.id(),
                null));
    }

    @PostMapping("/{id}/reject")
    @Operation(summary = "Einlösung ablehnen",
            description = "Nur für Administratoren. Die Punkte werden zurückgebucht; der Grund ist optional.")
    public Redemption reject(@PathVariable String id, @Valid @RequestBody(required = false) RejectRequest request) {
        FamilyMember viewer = currentMember.get();
        access.requireDecide(viewer);
        Redemption redemption = findPending(id, viewer);
        points.book(redemption.memberId(), redemption.cost(), "Belohnung abgelehnt: " + redemption.rewardName(),
                viewer.id());
        String reason = request == null || request.reason() == null || request.reason().isBlank()
                ? null : request.reason().strip();
        return redemptions.save(redemption.decided(RedemptionStatus.REJECTED, LocalDateTime.now(clock), viewer.id(),
                reason));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Offene Einlösung zurückziehen",
            description = "Durch die Person selbst oder einen Administrator. Die Punkte werden zurückgebucht.")
    public void withdraw(@PathVariable String id) {
        FamilyMember viewer = currentMember.get();
        Redemption redemption = findPending(id, viewer);
        boolean own = viewer.id().equals(redemption.memberId()) || viewer.id().equals(redemption.requestedBy());
        if (!own && !access.mayDecide(viewer)) {
            throw ApiException.forbidden("Du kannst nur deine eigenen Einlösungen zurückziehen.");
        }
        points.book(redemption.memberId(), redemption.cost(), "Einlösung zurückgezogen: " + redemption.rewardName(),
                viewer.id());
        redemptions.deleteById(id);
    }

    // Wiederholbare Belohnungen: höchstens eine offene Einlösung je Person. Einmalige: nur, solange die Person sie
    // weder offen noch genehmigt hat.
    private void requireRedeemable(Reward reward, String memberId) {
        if (!reward.repeatable() && redemptions.existsByMemberIdAndRewardIdAndStatusIn(memberId, reward.id(),
                Set.of(RedemptionStatus.PENDING, RedemptionStatus.APPROVED))) {
            throw ApiException.conflict("Diese Belohnung kann man nur einmal einlösen.");
        }
        if (redemptions.existsByMemberIdAndRewardIdAndStatusIn(memberId, reward.id(),
                Set.of(RedemptionStatus.PENDING))) {
            throw ApiException.conflict("Für diese Belohnung wartet schon eine Einlösung auf die Eltern.");
        }
    }

    // Einlösungen, die jemand nicht sehen darf, gelten für ihn als nicht vorhanden.
    private Redemption findPending(String id, FamilyMember viewer) {
        Redemption redemption = redemptions.findById(id)
                .filter(r -> access.seesFamily(viewer) || viewer.id().equals(r.memberId()))
                .orElseThrow(() -> ApiException.notFound("Einlösung " + id + " existiert nicht."));
        if (!redemption.isPending()) {
            throw ApiException.conflict("Über diese Einlösung ist bereits entschieden.");
        }
        return redemption;
    }
}
