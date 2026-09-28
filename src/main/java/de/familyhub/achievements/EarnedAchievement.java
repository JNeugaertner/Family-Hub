package de.familyhub.achievements;

import java.time.LocalDateTime;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.mapping.Document;

// Ein erreichter Erfolg; jeder Erfolg höchstens einmal je Person (eindeutiger Index). Name, Symbol und Bonus
// von damals werden mitgespeichert.
@Document("earnedAchievements")
@CompoundIndex(name = "member_achievement", def = "{'memberId': 1, 'achievementId': 1}", unique = true)
public record EarnedAchievement(
        @Id String id,
        String memberId,
        String achievementId,
        String name,
        String icon,
        int bonus,
        LocalDateTime earnedAt) {
}
