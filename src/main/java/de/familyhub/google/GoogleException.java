package de.familyhub.google;

// Fehler beim Aufruf von Google. reconnectNeeded: Google hat den Zugang widerrufen oder er ist abgelaufen
// (im Testmodus einer Google-App nach 7 Tagen); dann hilft nur neu verbinden.
public class GoogleException extends RuntimeException {

    private final boolean reconnectNeeded;

    public GoogleException(String message, boolean reconnectNeeded) {
        super(message);
        this.reconnectNeeded = reconnectNeeded;
    }

    public boolean reconnectNeeded() {
        return reconnectNeeded;
    }
}
