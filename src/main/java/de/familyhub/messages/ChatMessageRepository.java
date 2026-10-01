package de.familyhub.messages;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface ChatMessageRepository extends MongoRepository<ChatMessage, String> {

    List<ChatMessage> findTop200ByConversationOrderBySentAtDescIdDesc(String conversation);

    Optional<ChatMessage> findFirstByConversationOrderBySentAtDescIdDesc(String conversation);

    long countByConversationAndSenderIdNot(String conversation, String senderId);

    long countByConversationAndSenderIdNotAndSentAtAfter(String conversation, String senderId, LocalDateTime after);

    // Einzelchats eines Mitglieds: seine ID steckt in der Kennung, "family" enthält keine
    void deleteByConversationContaining(String memberId);
}
