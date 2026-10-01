package de.familyhub.messages;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface ReadMarkRepository extends MongoRepository<ReadMark, String> {

    void deleteByMemberId(String memberId);

    void deleteByConversationContaining(String memberId);
}
