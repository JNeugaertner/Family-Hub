package de.familyhub.achievements;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface AchievementProgressRepository extends MongoRepository<AchievementProgress, String> {

    Optional<AchievementProgress> findByMemberId(String memberId);

    void deleteByMemberId(String memberId);
}
