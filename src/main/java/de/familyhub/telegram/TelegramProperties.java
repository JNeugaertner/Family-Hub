package de.familyhub.telegram;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.util.StringUtils;

// Einstellungen für den Telegram-Bot der Familie. Den Bot-Token gibt @BotFather in Telegram aus; er steht nie im Git,
// sondern kommt aus der Umgebungsvariable TELEGRAM_BOT_TOKEN oder aus local.properties (familyhub.telegram.bot-token,
// siehe README). Ohne ihn ist Telegram "nicht eingerichtet".
@ConfigurationProperties("familyhub.telegram")
public record TelegramProperties(
        String botToken,
        // Nachrichten selbst bei Telegram abholen (Long Polling); so braucht FamilyHub keine öffentliche Adresse
        @DefaultValue("true") boolean polling,
        @DefaultValue("https://api.telegram.org") String apiUrl) {

    public boolean isConfigured() {
        return StringUtils.hasText(botToken);
    }
}
