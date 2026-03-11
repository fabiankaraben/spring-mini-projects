package com.example.dynamicscheduling.repository;

import com.example.dynamicscheduling.model.TaskConfig;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Spring Data JPA repository for {@link TaskConfig} entities.
 *
 * <p>Provides standard CRUD operations inherited from {@link JpaRepository}
 * plus a custom finder to look up tasks by their logical name.  The task name
 * is the primary business key used throughout the REST API.
 */
@Repository
public interface TaskConfigRepository extends JpaRepository<TaskConfig, Long> {

    /**
     * Finds a task configuration by its unique logical name.
     *
     * @param taskName the unique name of the task (e.g. {@code "heartbeat"})
     * @return an {@link Optional} containing the task config, or empty if not found
     */
    Optional<TaskConfig> findByTaskName(String taskName);

    /**
     * Checks whether a task configuration with the given name already exists.
     *
     * @param taskName the unique name of the task
     * @return {@code true} if a record with that name exists
     */
    boolean existsByTaskName(String taskName);
}
