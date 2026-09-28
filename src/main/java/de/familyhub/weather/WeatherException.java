package de.familyhub.weather;

// OpenWeather ist nicht erreichbar oder lehnt die Anfrage ab; die Meldung ist für die Oberfläche gedacht.
public class WeatherException extends RuntimeException {

    public WeatherException(String message) {
        super(message);
    }
}
