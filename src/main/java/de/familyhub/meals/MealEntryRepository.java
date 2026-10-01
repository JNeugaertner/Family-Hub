package de.familyhub.meals;

import java.time.LocalDate;
import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface MealEntryRepository extends MongoRepository<MealEntry, String> {

    // beide Grenzen einschließlich
    @Query("{ 'date': { $gte: ?0, $lte: ?1 } }")
    List<MealEntry> findInRange(LocalDate from, LocalDate to);

    List<MealEntry> findByDateAndTypeAndStatus(LocalDate date, MealType type, MealStatus status);

    List<MealEntry> findByDishId(String dishId);

    List<MealEntry> findByStatus(MealStatus status);

    // offene Wünsche eines gelöschten Mitglieds entfernen
    long deleteByCreatedByAndStatus(String createdBy, MealStatus status);
}
