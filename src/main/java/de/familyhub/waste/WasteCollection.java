package de.familyhub.waste;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.PersistenceCreator;
import org.springframework.data.mongodb.core.mapping.Document;

@Document("waste_collections")
public record WasteCollection(
        @Id String id,
        String fileName,
        LocalDateTime uploadedAt,
        int points,
        String assigneeId,
        String createdBy,
        List<Pickup> pickups) {

    public static final String ID = "family";

    @PersistenceCreator
    public WasteCollection {
        pickups = pickups == null ? List.of() : List.copyOf(pickups);
    }

    public record Pickup(String id, String type, LocalDate date) {
    }
}