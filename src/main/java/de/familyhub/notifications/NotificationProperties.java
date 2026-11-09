package de.familyhub.notifications;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

// Einstellungen für Benachrichtigungen. Wann die Erinnerungen laufen, steht direkt an den @Scheduled-Methoden
// (familyhub.notifications.due-reminder-cron und .pending-scan).
@ConfigurationProperties("familyhub.notifications")
public record NotificationProperties(
        // Termine, die so bald beginnen, gelten als kurzfristig
        @DefaultValue("PT24H") Duration shortNotice,
        // So lange darf eine Bestätigung offen sein, bevor FamilyHub erinnert
        @DefaultValue("PT24H") Duration pendingAfter,
        // Im Hintergrund senden, damit keine Anfrage auf Telegram wartet; Tests senden sofort
        @DefaultValue("true") boolean async) {
}
