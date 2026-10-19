package de.familyhub.whatsapp;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface WhatsAppMessageRepository extends MongoRepository<WhatsAppMessage, String> {

    List<WhatsAppMessage> findTop100ByOrderByReceivedAtDescIdDesc();
}
