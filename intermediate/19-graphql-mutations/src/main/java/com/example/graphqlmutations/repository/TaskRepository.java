package com.example.graphqlmutations.repository;

import com.example.graphqlmutations.domain.Task;
import com.example.graphqlmutations.domain.TaskStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link Task} entities.
 *
 * <p>Extending {@link JpaRepository} provides all standard CRUD operations without
 * any implementation code. The custom finder methods below are derived by Spring Data
 * from the method names, so no manual SQL or JPQL is needed.
 */
@Repository
public interface TaskRepository extends JpaRepository<Task, Long> {

    /**
     * Find all tasks belonging to a specific project.
     *
     * <p>Spring Data derives the query: {@code SELECT t FROM Task t WHERE t.project.id = ?1}.
     * This is a common pattern for fetching the child side of a one-to-many relationship
     * without loading the parent entity first.
     *
     * @param projectId the primary key of the project
     * @return list of tasks in that project (empty list if none)
     */
    List<Task> findByProjectId(Long projectId);

    /**
     * Find all tasks with a specific status.
     *
     * <p>Demonstrates querying by an enum field. Spring Data generates:
     * {@code SELECT t FROM Task t WHERE t.status = ?1}.
     *
     * @param status the task status to filter by
     * @return list of tasks in that status
     */
    List<Task> findByStatus(TaskStatus status);

    /**
     * Find all tasks belonging to a project filtered by status.
     *
     * <p>Useful for displaying, e.g., "all IN_PROGRESS tasks in Project X".
     * Spring Data generates:
     * {@code SELECT t FROM Task t WHERE t.project.id = ?1 AND t.status = ?2}.
     *
     * @param projectId the primary key of the project
     * @param status    the task status to filter by
     * @return filtered list of tasks
     */
    List<Task> findByProjectIdAndStatus(Long projectId, TaskStatus status);
}
