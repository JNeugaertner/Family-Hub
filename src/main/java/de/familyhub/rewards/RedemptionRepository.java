package de.familyhub.rewards;

import java.util.Collection;
import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface RedemptionRepository extends MongoRepository<Redemption, String> {

    List<Redemption> findAllByOrderByRequestedAtDesc();

    List<Redemption> findByMemberIdOrderByRequestedAtDesc(String memberId);

    boolean existsByMemberIdAndRewardIdAndStatusIn(String memberId, String rewardId,
            Collection<RedemptionStatus> statuses);

    boolean existsByRewardIdAndStatus(String rewardId, RedemptionStatus status);

    long countByMemberIdAndStatus(String memberId, RedemptionStatus status);
}
