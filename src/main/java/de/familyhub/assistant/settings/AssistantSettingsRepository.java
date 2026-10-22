package de.familyhub.assistant.settings;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface AssistantSettingsRepository extends MongoRepository<AssistantSettings, String> {
}
