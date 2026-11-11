package de.familyhub.calendar;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface EventTemplateRepository extends MongoRepository<EventTemplate, String> {
}
