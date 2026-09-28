package de.familyhub.achievements;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface AchievementRepository extends MongoRepository<Achievement, String> {

    List<Achievement> findByActiveTrue();

    Optional<Achievement> findByKey(String key);
}
