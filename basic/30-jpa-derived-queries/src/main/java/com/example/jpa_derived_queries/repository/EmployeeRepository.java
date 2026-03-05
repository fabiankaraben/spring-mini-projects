package com.example.jpa_derived_queries.repository;

import com.example.jpa_derived_queries.entity.Employee;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Repository interface for Employee entity.
 * Demonstrates Spring Data JPA Derived Query Methods.
 */
@Repository
public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    // 1. Basic findBy - Finds a list of employees by their department
    List<Employee> findByDepartment(String department);

    // 2. findBy with And - Finds a single employee by first name and last name
    Optional<Employee> findByFirstNameAndLastName(String firstName, String lastName);

    // 3. findBy with GreaterThan - Finds employees with a salary greater than the
    // specified amount
    List<Employee> findBySalaryGreaterThan(Double salary);

    // 4. findBy with True - Finds all active employees
    List<Employee> findByActiveTrue();

    // 5. findBy with Between - Finds employees hired between two dates
    List<Employee> findByHireDateBetween(LocalDate startDate, LocalDate endDate);

    // 6. findBy with OrderBy - Finds active employees and orders them by salary
    // descending
    List<Employee> findByActiveTrueOrderBySalaryDesc();

    // 7. findBy with StartingWith - Finds employees whose first name starts with
    // the given prefix
    List<Employee> findByFirstNameStartingWith(String prefix);
}
