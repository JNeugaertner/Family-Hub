package de.familyhub.preferences;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface MemberPreferencesRepository extends MongoRepository<MemberPreferences, String> {
}
