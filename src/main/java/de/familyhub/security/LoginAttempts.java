package de.familyhub.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

// Zählt fehlgeschlagene Anmeldungen je Benutzername (nur im Speicher, nach einem Neustart beginnt die Zählung neu).
// Nach maxFailures Fehlversuchen innerhalb der Sperrdauer ist das Konto für die Sperrdauer gesperrt;
// eine erfolgreiche Anmeldung setzt die Zählung zurück.
@Component
@EnableConfigurationProperties(LoginProperties.class)
public class LoginAttempts {

    private record Attempts(int failures, Instant lastFailure, Instant lockedUntil) {

        boolean lockedAt(Instant now) {
            return lockedUntil != null && now.isBefore(lockedUntil);
        }
    }

    private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();
    private final LoginProperties properties;
    private final Clock clock;

    public LoginAttempts(LoginProperties properties, Clock clock) {
        this.properties = properties;
        this.clock = clock;
    }

    // Verbleibende Sperrzeit, wenn das Konto gerade gesperrt ist
    public Optional<Duration> lockedFor(String username) {
        Instant now = clock.instant();
        Attempts current = attempts.get(key(username));
        return current != null && current.lockedAt(now)
                ? Optional.of(Duration.between(now, current.lockedUntil()))
                : Optional.empty();
    }

    // Zählt einen Fehlversuch; true, wenn das Konto damit gesperrt ist
    public boolean failed(String username) {
        Instant now = clock.instant();
        forgetExpired(now);
        Attempts updated = attempts.compute(key(username), (name, current) -> {
            int failures = current != null && isRecent(current, now) ? current.failures() + 1 : 1;
            return failures >= properties.maxFailures()
                    ? new Attempts(0, now, now.plus(properties.lockDuration()))
                    : new Attempts(failures, now, null);
        });
        return updated.lockedAt(now);
    }

    public void succeeded(String username) {
        attempts.remove(key(username));
    }

    // Nur für Tests: Zählung für alle Konten zurücksetzen
    void reset() {
        attempts.clear();
    }

    // Einträge ohne Sperre und ohne aktuelle Fehlversuche entfernen, damit die Liste nicht wächst
    private void forgetExpired(Instant now) {
        attempts.values().removeIf(a -> !a.lockedAt(now) && !isRecent(a, now));
    }

    // Letzter Fehlversuch liegt weniger als die Sperrdauer zurück
    private boolean isRecent(Attempts a, Instant now) {
        return a.lastFailure().plus(properties.lockDuration()).isAfter(now);
    }

    private static String key(String username) {
        return username == null ? "" : username.trim().toLowerCase(Locale.ROOT);
    }
}
