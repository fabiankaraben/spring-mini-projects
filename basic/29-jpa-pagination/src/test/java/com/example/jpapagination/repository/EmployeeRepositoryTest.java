package com.example.jpapagination.repository;

import com.example.jpapagination.model.Employee;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class EmployeeRepositoryTest {

    @Autowired
    private EmployeeRepository employeeRepository;

    @Test
    @DisplayName("Should retrieve paginated list of employees with sorting")
    void testFindAllPaginated() {
        // Arrange
        employeeRepository.save(new Employee("Dave", "Engineering", 80000.0));
        employeeRepository.save(new Employee("Eve", "Engineering", 85000.0));
        employeeRepository.save(new Employee("Frank", "Management", 90000.0));

        // Create a PageRequest starting from page 0, fetching 2 items at a time, sorted
        // by salary DESC
        Pageable pageable = PageRequest.of(0, 2, Sort.by("salary").descending());

        // Act
        Page<Employee> page = employeeRepository.findAll(pageable);

        // Assert
        assertThat(page.getTotalElements()).isEqualTo(3); // Total records across all pages
        assertThat(page.getTotalPages()).isEqualTo(2); // With size 2, total pages = CEIL(3 / 2) = 2
        assertThat(page.getContent()).hasSize(2); // First page contains up to 2 items
        assertThat(page.getContent().get(0).getName()).isEqualTo("Frank"); // Highest salary
        assertThat(page.getContent().get(1).getName()).isEqualTo("Eve"); // Second highest salary
    }

    @Test
    @DisplayName("Should retrieve paginated list of employees filtered by department")
    void testFindByDepartmentPaginated() {
        // Arrange
        employeeRepository.save(new Employee("Grace", "Sales", 60000.0));
        employeeRepository.save(new Employee("Hank", "Sales", 65000.0));
        employeeRepository.save(new Employee("Irene", "Marketing", 55000.0));

        // Let's retrieve page 0, size 1, sorting by salary ASC for the Sales department
        Pageable pageable = PageRequest.of(0, 1, Sort.by("salary").ascending());

        // Act
        Page<Employee> page = employeeRepository.findByDepartment("Sales", pageable);

        // Assert
        assertThat(page.getTotalElements()).isEqualTo(2); // Total "Sales" records
        assertThat(page.getContent()).hasSize(1);
        assertThat(page.getContent().get(0).getName()).isEqualTo("Grace"); // Lower salary first
    }
}
