package de.familyhub.inbox;

import org.springframework.data.mongodb.repository.MongoRepository;

// Abfragen nach Sichtbarkeit stehen in Inbox (MongoTemplate), hier nur das Speichern.
public interface InboundMessageRepository extends MongoRepository<InboundMessage, String> {
}
