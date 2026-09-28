package de.familyhub.google;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface GoogleConnectionRepository extends MongoRepository<GoogleConnection, String> {

    Optional<GoogleConnection> findByMemberId(String memberId);

    void deleteByMemberId(String memberId);
}
