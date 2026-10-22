package de.familyhub.assistant.chat;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface AssistantConversationRepository extends MongoRepository<AssistantConversation, String> {
}
