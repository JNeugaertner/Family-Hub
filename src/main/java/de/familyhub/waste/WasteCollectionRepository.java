package de.familyhub.waste;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface WasteCollectionRepository extends MongoRepository<WasteCollection, String> {

    default WasteCollection current() {
        return findById(WasteCollection.ID).orElse(null);
    }
}