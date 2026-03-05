package com.example.jpacustomqueries.repository;

import com.example.jpacustomqueries.entity.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository interface for Employee entity.
 * Demonstrates the use of @Query for custom JPQL and Native queries.
 */
@Repository
public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    /**
     * Custom JPQL query to find an employee by exact email match.
     * Uses named parameter.
     */
    @Query("SELECT e FROM Employee e WHERE e.email = :email")
    Optional<Employee> findByEmailExactly(@Param("email") String email);

    /**
     * Custom JPQL query with partial string matching (LIKE) and case
     * transformation.
     */
    @Query("SELECT e FROM Employee e WHERE LOWER(e.name) LIKE LOWER(CONCAT('%', :name, '%'))")
    List<Employee> findByNameContainingIgnoreCaseCustom(@Param("name") String name);

    /**
     * Custom Native query that executes directly against the SQL database.
     * Helpful when queries are complex or specific to the database engine.
     */
    @Query(value = "SELECT * FROM employees WHERE department = :dept", nativeQuery = true)
    List<Employee> findAllByDepartmentNative(@Param("dept") String department);

}
