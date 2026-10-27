package de.familyhub.settings;

import java.util.Set;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.mongodb.core.mapping.Document;

import de.familyhub.calendar.EventCategory;
import de.familyhub.weather.WeatherLocation;
import de.familyhub.ors.OrsLocation;

// Einstellungen der Familie, als ein einziges Dokument gespeichert.
@Document("settings")
public record FamilySettings(
        @Id String id,
        // Kategorien, deren (nicht private, freigegebene) Termine Gäste sehen (Entscheidung vom 22.09.2026)
        Set<EventCategory> guestCategories,
        // Wohnort für das Wetter; null, solange ihn kein Administrator eingestellt hat
        WeatherLocation weatherLocation,
        // Wohnort für die Routenberechnung; null, solange ihn kein Administrator eingestellt hat
        OrsLocation homeLocation) {

    public static final String ID = "family";

    @PersistenceCreator
    public FamilySettings {
        guestCategories = guestCategories == null ? Set.of() : Set.copyOf(guestCategories);
    }

    public FamilySettings(String id, Set<EventCategory> guestCategories) {
        this(id, guestCategories, null, null);
    }

    public static FamilySettings defaults() {
        return new FamilySettings(ID, Set.of());
    }

    public FamilySettings withGuestCategories(Set<EventCategory> categories) {
        return new FamilySettings(ID, categories, weatherLocation, homeLocation);
    }

    public FamilySettings withWeatherLocation(WeatherLocation location) {
        return new FamilySettings(ID, guestCategories, location, homeLocation);
    }

    public FamilySettings withHomeLocation(OrsLocation homeLocation) {
        return new FamilySettings(ID, guestCategories, weatherLocation, homeLocation);
    }
}
