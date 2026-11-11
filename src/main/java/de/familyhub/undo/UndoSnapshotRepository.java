package de.familyhub.undo;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface UndoSnapshotRepository extends MongoRepository<UndoSnapshot, String> {
}
