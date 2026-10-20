package de.familyhub.google;

// Fehler beim Aufruf von Google. reconnectNeeded: Google hat den Zugang widerrufen oder er ist abgelaufen
// (im Testmodus einer Google-App nach 7 Tagen); dann hilft nur neu verbinden. notFound: Kalender oder Termin gibt es
// bei Google nicht (mehr), z. B. weil jemand den Kalender „FamilyHub“ in Google gelöscht hat.
public class GoogleException extends RuntimeException {

    private final boolean reconnectNeeded;
    private final boolean notFound;

    public GoogleException(String message, boolean reconnectNeeded) {
        this(message, reconnectNeeded, false);
    }

    private GoogleException(String message, boolean reconnectNeeded, boolean notFound) {
        super(message);
        this.reconnectNeeded = reconnectNeeded;
        this.notFound = notFound;
    }

    public static GoogleException notFound(String message) {
        return new GoogleException(message, false, true);
    }

    public boolean reconnectNeeded() {
        return reconnectNeeded;
    }

    public boolean notFound() {
        return notFound;
    }
}
