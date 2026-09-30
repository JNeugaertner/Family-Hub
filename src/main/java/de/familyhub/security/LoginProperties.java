package de.familyhub.security;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

// Schutz gegen das Durchprobieren von Passwörtern: so viele Fehlversuche innerhalb der Sperrdauer sperren das
// Konto für die Sperrdauer.
@ConfigurationProperties("familyhub.login")
public record LoginProperties(
        @DefaultValue("5") int maxFailures,
        @DefaultValue("PT5M") Duration lockDuration) {
}
