package de.familyhub.rewards;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface RewardRepository extends MongoRepository<Reward, String> {

    List<Reward> findAllByOrderByCostAsc();
}
