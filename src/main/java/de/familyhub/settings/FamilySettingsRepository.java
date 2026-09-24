package de.familyhub.settings;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface FamilySettingsRepository extends MongoRepository<FamilySettings, String> {

    default FamilySettings current() {
        return findById(FamilySettings.ID).orElseGet(FamilySettings::defaults);
    }
}
