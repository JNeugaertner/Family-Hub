package de.familyhub.assistant.llm;

// Fehler beim Aufruf des Sprachmodells. Die Meldung ist für Menschen gedacht und enthält nie den API-Schlüssel.
public class LanguageModelException extends RuntimeException {

    public LanguageModelException(String message) {
        super(message);
    }
}
