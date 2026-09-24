package de.familyhub.settings;

import java.util.Set;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.mongodb.core.mapping.Document;

import de.familyhub.calendar.EventCategory;

// Einstellungen der Familie, als ein einziges Dokument gespeichert.
@Document("settings")
public record FamilySettings(
        @Id String id,
        // Kategorien, deren (nicht private, freigegebene) Termine Gäste sehen (Entscheidung vom 22.09.2026)
        Set<EventCategory> guestCategories) {

    public static final String ID = "family";

    @PersistenceCreator
    public FamilySettings {
        guestCategories = guestCategories == null ? Set.of() : Set.copyOf(guestCategories);
    }

    public static FamilySettings defaults() {
        return new FamilySettings(ID, Set.of());
    }
}
