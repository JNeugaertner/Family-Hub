package de.familyhub.rewards;

import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import com.fasterxml.jackson.annotation.JsonIgnore;

import io.swagger.v3.oas.annotations.media.Schema;

// Eine eingelöste Belohnung. Name, Emoji und Kosten werden mitgespeichert, damit der Verlauf auch dann stimmt,
// wenn die Belohnung später geändert oder gelöscht wird. Die Punkte sind beim Einlösen schon abgezogen.
@Document("redemptions")
public record Redemption(
        @Id String id,
        String rewardId,
        String rewardName,
        String rewardEmoji,
        int cost,
        @Schema(description = "Wer die Belohnung bekommt")
        String memberId,
        RedemptionStatus status,
        LocalDateTime requestedAt,
        @Schema(description = "Wer eingelöst hat (die Person selbst oder ein Administrator)")
        String requestedBy,
        LocalDateTime decidedAt,
        String decidedBy,
        @Schema(description = "Grund der Ablehnung, falls angegeben")
        String rejectReason) {

    @JsonIgnore
    public boolean isPending() {
        return status == RedemptionStatus.PENDING;
    }

    public Redemption decided(RedemptionStatus newStatus, LocalDateTime at, String by, String reason) {
        return new Redemption(id, rewardId, rewardName, rewardEmoji, cost, memberId, newStatus, requestedAt,
                requestedBy, at, by, reason);
    }
}
