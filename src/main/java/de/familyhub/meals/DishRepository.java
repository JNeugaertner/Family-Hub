package de.familyhub.meals;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface DishRepository extends MongoRepository<Dish, String> {
}
