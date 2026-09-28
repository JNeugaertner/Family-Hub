package de.familyhub.rewards;

import java.net.URI;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import de.familyhub.family.FamilyMember;
import de.familyhub.security.CurrentMember;
import de.familyhub.web.ApiException;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/rewards")
@Tag(name = "Belohnungen")
public class RewardController {

    private final RewardRepository rewards;
    private final RedemptionRepository redemptions;
    private final CurrentMember currentMember;
    private final RewardAccess access;

    public RewardController(RewardRepository rewards, RedemptionRepository redemptions, CurrentMember currentMember,
            RewardAccess access) {
        this.rewards = rewards;
        this.redemptions = redemptions;
        this.currentMember = currentMember;
        this.access = access;
    }

    @GetMapping
    @Operation(summary = "Belohnungen", description = "Nach Punkten sortiert. Inaktive nur für Administratoren.")
    public List<Reward> list() {
        FamilyMember viewer = currentMember.get();
        access.requireView(viewer);
        boolean all = access.mayManage(viewer);
        return rewards.findAllByOrderByCostAsc().stream().filter(r -> all || r.active()).toList();
    }

    @PostMapping
    @Operation(summary = "Belohnung anlegen", description = "Nur für Administratoren.")
    public ResponseEntity<Reward> create(@Valid @RequestBody Reward reward) {
        access.requireManage(currentMember.get());
        Reward saved = rewards.save(reward.withId(null));
        URI location = ServletUriComponentsBuilder.fromCurrentRequest().path("/{id}").buildAndExpand(saved.id()).toUri();
        return ResponseEntity.created(location).body(saved);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Belohnung ändern",
            description = "Nur für Administratoren. Bereits eingelöste Belohnungen behalten Name und Preis von damals.")
    public Reward update(@PathVariable String id, @Valid @RequestBody Reward reward) {
        access.requireManage(currentMember.get());
        find(id);
        return rewards.save(reward.withId(id));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(summary = "Belohnung löschen",
            description = "Nur für Administratoren und nur ohne offene Einlösungen. Der Einlöseverlauf bleibt erhalten.")
    public void delete(@PathVariable String id) {
        access.requireManage(currentMember.get());
        find(id);
        if (redemptions.existsByRewardIdAndStatus(id, RedemptionStatus.PENDING)) {
            throw ApiException.conflict("Für diese Belohnung warten noch Einlösungen auf eine Entscheidung. "
                    + "Bitte zuerst genehmigen oder ablehnen.");
        }
        rewards.deleteById(id);
    }

    private Reward find(String id) {
        return rewards.findById(id).orElseThrow(() -> ApiException.notFound("Belohnung " + id + " existiert nicht."));
    }
}
