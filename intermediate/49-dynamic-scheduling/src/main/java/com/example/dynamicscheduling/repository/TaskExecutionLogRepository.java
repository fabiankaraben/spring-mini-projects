package com.example.dynamicscheduling.repository;

import com.example.dynamicscheduling.model.TaskExecutionLog;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Spring Data JPA repository for {@link TaskExecutionLog} entities.
 *
 * <p>Provides standard CRUD operations plus paginated queries to retrieve
 * execution history.  Paging is important here because log tables grow over
 * time and returning all rows at once would be impractical.
 */
@Repository
public interface TaskExecutionLogRepository extends JpaRepository<TaskExecutionLog, Long> {

    /**
     * Returns a paginated slice of execution log entries ordered by fired
     * time descending (newest first) for a specific task.
     *
     * @param taskName the logical task name to filter by
     * @param pageable paging and sorting parameters supplied by the caller
     * @return page of execution log entries matching the task name
     */
    Page<TaskExecutionLog> findByTaskNameOrderByFiredAtDesc(String taskName, Pageable pageable);

    /**
     * Returns a paginated slice of ALL execution log entries, ordered by
     * fired time descending (newest first).
     *
     * @param pageable paging and sorting parameters supplied by the caller
     * @return page of all execution log entries
     */
    Page<TaskExecutionLog> findAllByOrderByFiredAtDesc(Pageable pageable);
}
