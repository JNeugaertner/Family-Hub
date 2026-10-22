package de.familyhub.assistant;

import java.time.Instant;
import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface AssistantCallRepository extends MongoRepository<AssistantCall, String> {

    List<AssistantCall> findByMemberIdOrderByAtAsc(String memberId);

    List<AssistantCall> findByAtGreaterThanEqual(Instant from);

    void deleteByMemberId(String memberId);
}
