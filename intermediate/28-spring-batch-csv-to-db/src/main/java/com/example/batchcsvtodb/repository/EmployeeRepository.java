package com.example.batchcsvtodb.repository;

import com.example.batchcsvtodb.model.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link Employee} entities.
 *
 * <p>Spring Data automatically provides standard CRUD operations (save, findById,
 * findAll, delete, count, etc.) by extending {@link JpaRepository}.
 *
 * <p>Custom query methods below are derived from their method names following
 * Spring Data's "query derivation" mechanism – no SQL or JPQL is needed.
 */
@Repository
public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    /**
     * Finds all employees belonging to a specific department.
     *
     * <p>Spring Data translates this into:
     * {@code SELECT e FROM Employee e WHERE e.department = :department}
     *
     * @param department the department name to filter by (case-sensitive)
     * @return list of employees in the given department; empty list if none found
     */
    List<Employee> findByDepartment(String department);

    /**
     * Looks up an employee by their email address.
     *
     * <p>Since email is a unique column, at most one result is returned.
     *
     * @param email the email address to look up
     * @return an {@link Optional} containing the employee, or empty if not found
     */
    Optional<Employee> findByEmail(String email);

    /**
     * Checks whether an employee with the given email already exists in the database.
     *
     * <p>Useful for de-duplication logic in the processor or controller.
     *
     * @param email the email address to check
     * @return {@code true} if at least one employee with that email exists
     */
    boolean existsByEmail(String email);
}
