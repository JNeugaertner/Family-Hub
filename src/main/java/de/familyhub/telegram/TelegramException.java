package de.familyhub.telegram;

// Fehler beim Aufruf von Telegram. Die Meldung ist für Menschen gedacht und enthält nie den Bot-Token.
public class TelegramException extends RuntimeException {

    public TelegramException(String message) {
        super(message);
    }
}
