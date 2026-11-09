package de.familyhub.telegram;

import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface TelegramLinkRepository extends MongoRepository<TelegramLink, String> {

    Optional<TelegramLink> findByMemberId(String memberId);

    Optional<TelegramLink> findByTelegramUserId(long telegramUserId);

    void deleteByMemberId(String memberId);
}
