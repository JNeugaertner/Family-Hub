package de.familyhub.assistant.tools;

// Ein Werkzeug kann eine Anfrage des Modells nicht ausführen; die Meldung geht an das Modell zurück
public class ToolException extends RuntimeException {

    public ToolException(String message) {
        super(message);
    }
}
