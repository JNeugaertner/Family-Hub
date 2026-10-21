package de.familyhub.telegram;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

// Einmal-Codes zum Verknüpfen: FamilyHub erzeugt sie für die angemeldete Person, Telegram schickt sie zurück, wenn
// die Person den Bot über den Link startet ("/start <code>"). So beweist sie, dass ihr beides gehört: das
// FamilyHub-Konto und das Telegram-Konto. Ein Code gilt zehn Minuten und nur einmal; die Codes liegen nur im
// Speicher, ein Neustart macht offene ungültig.
@Component
class TelegramLinkCodes {

    static final Duration VALIDITY = Duration.ofMinutes(10);

    private static final SecureRandom RANDOM = new SecureRandom();

    private record Pending(String memberId, Instant expiresAt) {
    }

    private final Map<String, Pending> codes = new ConcurrentHashMap<>();
    private final Clock clock;

    TelegramLinkCodes(Clock clock) {
        this.clock = clock;
    }

    // Ersetzt einen noch offenen Code derselben Person
    String create(String memberId) {
        Instant now = clock.instant();
        codes.values().removeIf(p -> p.memberId().equals(memberId) || !p.expiresAt().isAfter(now));
        byte[] bytes = new byte[18];
        RANDOM.nextBytes(bytes);
        // Base64url: nur Zeichen, die Telegram im Start-Parameter erlaubt (A-Z, a-z, 0-9, _ und -)
        String code = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        codes.put(code, new Pending(memberId, now.plus(VALIDITY)));
        return code;
    }

    // Id des Mitglieds, wenn der Code gültig ist; danach ist er verbraucht
    Optional<String> redeem(String code) {
        Pending pending = codes.remove(code);
        if (pending == null || !pending.expiresAt().isAfter(clock.instant())) {
            return Optional.empty();
        }
        return Optional.of(pending.memberId());
    }
}
