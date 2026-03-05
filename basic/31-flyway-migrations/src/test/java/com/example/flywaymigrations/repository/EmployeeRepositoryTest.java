package com.example.flywaymigrations.repository;

import com.example.flywaymigrations.entity.Employee;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for EmployeeRepository.
 * 
 * @DataJpaTest configures an in-memory database and tests JPA behavior.
 *              With Flyway included and auto-configured, Flyway will run its
 *              migrations
 *              against the test database before the tests execute.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
public class EmployeeRepositoryTest {

    @Autowired
    private EmployeeRepository employeeRepository;

    @Test
    public void testFlywayMigrationsApplied() {
        // Since V2__Insert_initial_employees.sql inserted two rows, they should be here
        List<Employee> employees = employeeRepository.findAll();
        assertThat(employees).hasSizeGreaterThanOrEqualTo(2);

        Optional<Employee> alice = employees.stream()
                .filter(e -> "Alice Smith".equals(e.getName()))
                .findFirst();

        assertThat(alice).isPresent();
        assertThat(alice.get().getDepartment()).isEqualTo("Engineering"); // Set by V3
    }

    @Test
    public void testSaveAndFindEmployee() {
        // Arrange
        Employee newEmp = new Employee("Charles", "charles@example.com", "Marketing");

        // Act
        Employee saved = employeeRepository.save(newEmp);
        Optional<Employee> found = employeeRepository.findById(saved.getId());

        // Assert
        assertThat(found).isPresent();
        assertThat(found.get().getName()).isEqualTo("Charles");
        assertThat(found.get().getEmail()).isEqualTo("charles@example.com");
        assertThat(found.get().getDepartment()).isEqualTo("Marketing");
    }
}
