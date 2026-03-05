package com.example.jpacustomqueries.repository;

import com.example.jpacustomqueries.entity.Employee;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sliced integration test testing the Repository boundary.
 * Defaults to an in-memory embedded DB for isolated tests.
 */
@DataJpaTest
public class EmployeeRepositoryTest {

    @Autowired
    private EmployeeRepository employeeRepository;

    @BeforeEach
    void setUp() {
        // Seed some data in the testing embedded database
        Employee e1 = new Employee("Alice Brown", "alice@example.com", "IT", 6000);
        Employee e2 = new Employee("Bob Smith", "bob@example.com", "HR", 5000);
        Employee e3 = new Employee("Charlie Brown", "charlie@example.com", "IT", 7000);

        employeeRepository.saveAll(List.of(e1, e2, e3));
    }

    @AfterEach
    void tearDown() {
        employeeRepository.deleteAll();
    }

    @Test
    void shouldFindByEmailExactly() {
        Optional<Employee> empOpt = employeeRepository.findByEmailExactly("alice@example.com");

        assertThat(empOpt).isPresent();
        assertThat(empOpt.get().getName()).isEqualTo("Alice Brown");
    }

    @Test
    void shouldFindByNameContainingIgnoreCaseCustom() {
        // We look for 'brown', expecting case-insensitive match for Alice Brown and
        // Charlie Brown
        List<Employee> results = employeeRepository.findByNameContainingIgnoreCaseCustom("brown");

        assertThat(results).hasSize(2);
        assertThat(results).extracting(Employee::getName).containsExactlyInAnyOrder("Alice Brown", "Charlie Brown");
    }

    @Test
    void shouldFindAllByDepartmentNative() {
        List<Employee> results = employeeRepository.findAllByDepartmentNative("IT");

        assertThat(results).hasSize(2);
        assertThat(results).extracting(Employee::getEmail).containsExactlyInAnyOrder("alice@example.com",
                "charlie@example.com");
    }
}
