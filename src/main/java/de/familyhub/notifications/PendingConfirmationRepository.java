package de.familyhub.notifications;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface PendingConfirmationRepository extends MongoRepository<PendingConfirmation, String> {
}
