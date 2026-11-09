package de.familyhub.notifications;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface NotificationSettingsRepository extends MongoRepository<NotificationSettings, String> {
}
