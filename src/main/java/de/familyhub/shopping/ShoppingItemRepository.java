package de.familyhub.shopping;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface ShoppingItemRepository extends MongoRepository<ShoppingItem, String> {

    List<ShoppingItem> findAllByOrderByCreatedAtAsc();

    List<ShoppingItem> findByCheckedTrueAndStatus(ShoppingItemStatus status);
}
