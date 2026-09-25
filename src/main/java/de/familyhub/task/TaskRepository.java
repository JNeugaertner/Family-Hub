package de.familyhub.task;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface TaskRepository extends MongoRepository<Task, String> {

    List<Task> findByAssigneeIdOrderByDueDateAsc(String assigneeId);

    long countByAssigneeId(String assigneeId);
}
