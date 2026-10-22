package de.familyhub.assistant;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface AssistantCallRepository extends MongoRepository<AssistantCall, String> {

    List<AssistantCall> findByMemberIdOrderByAtAsc(String memberId);

    void deleteByMemberId(String memberId);
}
