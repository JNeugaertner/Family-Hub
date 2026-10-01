package de.familyhub.task;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface TaskRepository extends MongoRepository<Task, String> {

    List<Task> findByAssigneeIdOrderByDueDateAsc(String assigneeId);

    List<Task> findByTitleAndDueDateAndDescription(String title, java.time.LocalDate dueDate, String description);

    List<Task> findByDescription(String description);

    long countByAssigneeId(String assigneeId);
}
