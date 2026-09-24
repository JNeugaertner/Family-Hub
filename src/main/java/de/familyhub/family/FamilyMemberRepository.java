package de.familyhub.family;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface FamilyMemberRepository extends MongoRepository<FamilyMember, String> {
}
