package de.familyhub.assistant.drafts;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface DraftRepository extends MongoRepository<Draft, String> {

    Optional<Draft> findByIdAndMemberId(String id, String memberId);

    List<Draft> findByIdInAndMemberId(Collection<String> ids, String memberId);

    void deleteByMemberId(String memberId);
}
