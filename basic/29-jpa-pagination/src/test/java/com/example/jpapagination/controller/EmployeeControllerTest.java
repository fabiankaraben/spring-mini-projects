package com.example.jpapagination.controller;

import com.example.jpapagination.model.Employee;
import com.example.jpapagination.service.EmployeeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.springframework.test.context.ActiveProfiles;

@WebMvcTest(EmployeeController.class)
@ActiveProfiles("test")
class EmployeeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // Use @MockitoBean to replace deprecated @MockBean
    @MockitoBean
    private EmployeeService employeeService;

    @Test
    @DisplayName("Should return a paginated list of all employees")
    void testGetAllEmployeesPaginated() throws Exception {
        // Arrange
        Employee e1 = new Employee("Alice", "IT", 60000.0);
        Employee e2 = new Employee("Bob", "HR", 50000.0);
        List<Employee> employeeList = List.of(e1, e2);
        Page<Employee> page = new PageImpl<>(employeeList);

        // Mock the service to return the custom page regardless of Pageable param
        when(employeeService.getAllEmployees(any(Pageable.class))).thenReturn(page);

        // Act & Assert
        // We pass custom query parameters for pagination
        mockMvc.perform(get("/api/employees?page=0&size=2")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Alice"))
                .andExpect(jsonPath("$.content[1].name").value("Bob"))
                .andExpect(jsonPath("$.totalElements").value(2));
    }

    @Test
    @DisplayName("Should return a paginated list filtered by department")
    void testGetEmployeesByDepartmentPaginated() throws Exception {
        // Arrange
        Employee e3 = new Employee("Charlie", "Sales", 70000.0);
        Page<Employee> page = new PageImpl<>(List.of(e3));

        // Mock the service method using argument matchers
        when(employeeService.getEmployeesByDepartment(eq("Sales"), any(Pageable.class))).thenReturn(page);

        // Act & Assert
        mockMvc.perform(get("/api/employees/department?department=Sales&page=0&size=1")
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].name").value("Charlie"))
                .andExpect(jsonPath("$.content[0].department").value("Sales"))
                .andExpect(jsonPath("$.totalElements").value(1));
    }
}
