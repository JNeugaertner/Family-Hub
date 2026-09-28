package de.familyhub.achievements;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface EarnedAchievementRepository extends MongoRepository<EarnedAchievement, String> {

    List<EarnedAchievement> findByMemberIdOrderByEarnedAtDesc(String memberId);

    boolean existsByMemberIdAndAchievementId(String memberId, String achievementId);

    void deleteByMemberId(String memberId);
}
