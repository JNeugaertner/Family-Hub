package de.familyhub.whatsapp;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface WhatsAppMessageRepository extends MongoRepository<WhatsAppMessage, String> {

    List<WhatsAppMessage> findTop100ByOrderByReceivedAtDescIdDesc();

    // from: Absendernummer, wie WhatsApp sie liefert (nur Ziffern mit Ländervorwahl)
    List<WhatsAppMessage> findTop100ByFromOrderByReceivedAtDescIdDesc(String from);

    long countByFrom(String from);
}
