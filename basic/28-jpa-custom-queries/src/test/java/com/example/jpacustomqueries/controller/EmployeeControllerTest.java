package com.example.jpacustomqueries.controller;

import com.example.jpacustomqueries.entity.Employee;
import com.example.jpacustomqueries.service.EmployeeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
// USING @MockitoBean TO REPLACE THE DEPRECATED @MockBean!
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Controller sliced test, booting up only the web layer components context.
 */
@WebMvcTest(EmployeeController.class)
public class EmployeeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // Use MockitoBean rather than MockBean due to Deprecation and future removal in
    // Spring Boot 3.4
    @MockitoBean
    private EmployeeService service;

    @Test
    void testGetByEmail_Success() throws Exception {
        Employee employee = new Employee("Alice", "alice@example.com", "IT", 5000);

        // Mock Service Layer logic
        given(service.getEmployeeByEmail("alice@example.com")).willReturn(Optional.of(employee));

        // Test Endpoint Call
        mockMvc.perform(get("/api/employees/search/email")
                .param("email", "alice@example.com"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Alice"))
                .andExpect(jsonPath("$.email").value("alice@example.com"));
    }

    @Test
    void testGetByEmail_NotFound() throws Exception {
        // Return blank Optional for missing entities
        given(service.getEmployeeByEmail(anyString())).willReturn(Optional.empty());

        // Test endpoint call yields HTTP 404
        mockMvc.perform(get("/api/employees/search/email")
                .param("email", "unknown@test.com"))
                .andExpect(status().isNotFound());
    }
}
