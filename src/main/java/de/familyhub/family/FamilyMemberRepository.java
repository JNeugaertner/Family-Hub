package de.familyhub.family;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface FamilyMemberRepository extends MongoRepository<FamilyMember, String> {

    Optional<FamilyMember> findByUsername(String username);

    boolean existsByUsername(String username);
}
