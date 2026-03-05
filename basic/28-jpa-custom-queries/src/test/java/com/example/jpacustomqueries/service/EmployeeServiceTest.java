package com.example.jpacustomqueries.service;

import com.example.jpacustomqueries.entity.Employee;
import com.example.jpacustomqueries.repository.EmployeeRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

/**
 * Pure Java unit test using MockitoExtension for Mocking.
 * Fast, relying completely on Mocking behavior rather than Spring context.
 */
@ExtendWith(MockitoExtension.class)
public class EmployeeServiceTest {

    @Mock
    private EmployeeRepository repository;

    @InjectMocks
    private EmployeeService service;

    private Employee employee;

    @BeforeEach
    void setUp() {
        employee = new Employee("Alice", "alice@example.com", "IT", 5000);
    }

    @Test
    void testGetEmployeeByEmail() {
        // Arrange
        given(repository.findByEmailExactly("alice@example.com")).willReturn(Optional.of(employee));

        // Act
        Optional<Employee> result = service.getEmployeeByEmail("alice@example.com");

        // Assert
        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("Alice");
        verify(repository).findByEmailExactly("alice@example.com");
    }

    @Test
    void testSearchByName() {
        // Arrange
        given(repository.findByNameContainingIgnoreCaseCustom("ali")).willReturn(List.of(employee));

        // Act
        List<Employee> result = service.searchByName("ali");

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getEmail()).isEqualTo("alice@example.com");
    }

    @Test
    void testGetEmployeesByDepartment() {
        // Arrange
        given(repository.findAllByDepartmentNative("IT")).willReturn(List.of(employee));

        // Act
        List<Employee> result = service.getEmployeesByDepartment("IT");

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).getName()).isEqualTo("Alice");
    }
}
