package de.familyhub.calendar;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

public interface EventSeriesRepository extends MongoRepository<EventSeries, String> {

    @Query("{ 'template.memberIds': ?0 }")
    List<EventSeries> findByMember(String memberId);
}
