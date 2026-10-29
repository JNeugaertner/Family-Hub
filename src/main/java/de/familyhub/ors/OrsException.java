package de.familyhub.ors;

// OpenRouteService ist nicht erreichbar oder lehnt die Anfrage ab; die Meldung ist für die Oberfläche gedacht.
public class OrsException extends RuntimeException {

    public OrsException(String message) {
        super(message);
    }
}
