package com.example.jpa_derived_queries.repository;

import com.example.jpa_derived_queries.entity.Employee;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sliced integration test for {@link EmployeeRepository}.
 * Uses an embedded H2 database.
 */
@DataJpaTest
class EmployeeRepositoryTest {

    @Autowired
    private EmployeeRepository employeeRepository;

    @BeforeEach
    void setUp() {
        // Clear DB and insert fresh data before each test
        employeeRepository.deleteAll();
        employeeRepository.saveAll(List.of(
                new Employee("John", "Doe", "Engineering", 100000.0, LocalDate.of(2021, 5, 10), true),
                new Employee("Jane", "Doe", "Engineering", 110000.0, LocalDate.of(2020, 3, 15), true),
                new Employee("Jim", "Beam", "Sales", 60000.0, LocalDate.of(2022, 1, 20), false),
                new Employee("Anna", "Smith", "HR", 75000.0, LocalDate.of(2021, 11, 1), true)));
    }

    @Test
    void shouldFindEmployeesByDepartment() {
        List<Employee> engineers = employeeRepository.findByDepartment("Engineering");

        assertThat(engineers).hasSize(2);
        assertThat(engineers).extracting(Employee::getFirstName)
                .containsExactlyInAnyOrder("John", "Jane");
    }

    @Test
    void shouldFindEmployeeByFirstNameAndLastName() {
        Optional<Employee> found = employeeRepository.findByFirstNameAndLastName("Jane", "Doe");

        assertThat(found).isPresent();
        assertThat(found.get().getSalary()).isEqualTo(110000.0);
    }

    @Test
    void shouldFindEmployeesWithSalaryGreaterThan() {
        List<Employee> highEarners = employeeRepository.findBySalaryGreaterThan(80000.0);

        assertThat(highEarners).hasSize(2);
        assertThat(highEarners).extracting(Employee::getFirstName)
                .containsExactlyInAnyOrder("John", "Jane");
    }

    @Test
    void shouldFindActiveEmployees() {
        List<Employee> activeEmployees = employeeRepository.findByActiveTrue();

        assertThat(activeEmployees).hasSize(3); // John, Jane, Anna
        assertThat(activeEmployees).extracting(Employee::getFirstName)
                .doesNotContain("Jim");
    }

    @Test
    void shouldFindEmployeesHiredBetweenDates() {
        LocalDate start = LocalDate.of(2021, 1, 1);
        LocalDate end = LocalDate.of(2021, 12, 31);

        List<Employee> hiredIn2021 = employeeRepository.findByHireDateBetween(start, end);

        assertThat(hiredIn2021).hasSize(2);
        assertThat(hiredIn2021).extracting(Employee::getFirstName)
                .containsExactlyInAnyOrder("John", "Anna");
    }

    @Test
    void shouldFindActiveEmployeesOrderedBySalaryDesc() {
        List<Employee> orderedActive = employeeRepository.findByActiveTrueOrderBySalaryDesc();

        assertThat(orderedActive).hasSize(3);
        // Correct order: Jane(110000), John(100000), Anna(75000)
        assertThat(orderedActive.get(0).getFirstName()).isEqualTo("Jane");
        assertThat(orderedActive.get(1).getFirstName()).isEqualTo("John");
        assertThat(orderedActive.get(2).getFirstName()).isEqualTo("Anna");
    }

    @Test
    void shouldFindEmployeesWhoseFirstNameStartsWith() {
        List<Employee> startingWithJ = employeeRepository.findByFirstNameStartingWith("J");

        assertThat(startingWithJ).hasSize(3); // John, Jane, Jim
        assertThat(startingWithJ).extracting(Employee::getFirstName)
                .containsExactlyInAnyOrder("John", "Jane", "Jim");
    }
}
