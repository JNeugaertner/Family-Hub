package de.familyhub.points;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface PointEntryRepository extends MongoRepository<PointEntry, String> {

    List<PointEntry> findByMemberIdOrderByCreatedAtDesc(String memberId);

    List<PointEntry> findAllByOrderByCreatedAtDesc();
}
