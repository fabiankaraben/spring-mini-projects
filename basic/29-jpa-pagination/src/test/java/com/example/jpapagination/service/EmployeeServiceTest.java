package com.example.jpapagination.service;

import com.example.jpapagination.model.Employee;
import com.example.jpapagination.repository.EmployeeRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmployeeServiceTest {

    @Mock
    private EmployeeRepository employeeRepository;

    @InjectMocks
    private EmployeeService employeeService;

    @Test
    @DisplayName("Should returning a paginated list of all employees")
    void testGetAllEmployeesPaginated() {
        // Arrange
        Employee e1 = new Employee("Jack", "IT", 60000.0);
        Employee e2 = new Employee("Jill", "HR", 50000.0);
        Page<Employee> page = new PageImpl<>(List.of(e1, e2));
        Pageable pageable = PageRequest.of(0, 10);

        // Define mock behavior
        when(employeeRepository.findAll(any(Pageable.class))).thenReturn(page);

        // Act
        Page<Employee> result = employeeService.getAllEmployees(pageable);

        // Assert
        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent().get(0).getName()).isEqualTo("Jack");

        // Verify the repository was called with the correct argument
        verify(employeeRepository).findAll(pageable);
    }

    @Test
    @DisplayName("Should returning a paginated list filtered by department")
    void testGetEmployeesByDepartmentPaginated() {
        // Arrange
        Employee e3 = new Employee("Karl", "IT", 70000.0);
        Page<Employee> page = new PageImpl<>(List.of(e3));
        Pageable pageable = PageRequest.of(0, 5);

        // Define mock behavior
        when(employeeRepository.findByDepartment(eq("IT"), any(Pageable.class))).thenReturn(page);

        // Act
        Page<Employee> result = employeeService.getEmployeesByDepartment("IT", pageable);

        // Assert
        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getDepartment()).isEqualTo("IT");

        // Verify the method call on repository
        verify(employeeRepository).findByDepartment("IT", pageable);
    }
}
