package com.example.flywaymigrations.controller;

import com.example.flywaymigrations.entity.Employee;
import com.example.flywaymigrations.repository.EmployeeRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Unit tests for EmployeeController.
 * Using @WebMvcTest slices the context to only include web-layer beans.
 */
@WebMvcTest(EmployeeController.class)
public class EmployeeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // Using @MockitoBean as per project requirements (replaces deprecated
    // @MockBean)
    @MockitoBean
    private EmployeeRepository employeeRepository;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    public void testGetAllEmployees() throws Exception {
        Employee emp = new Employee("John Doe", "john@example.com", "Sales");
        emp.setId(1L);

        Mockito.when(employeeRepository.findAll()).thenReturn(List.of(emp));

        mockMvc.perform(get("/api/employees"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()").value(1))
                .andExpect(jsonPath("$[0].name").value("John Doe"));
    }

    @Test
    public void testGetEmployeeById_Found() throws Exception {
        Employee emp = new Employee("Jane Doe", "jane@example.com", "HR");
        emp.setId(2L);

        Mockito.when(employeeRepository.findById(2L)).thenReturn(Optional.of(emp));

        mockMvc.perform(get("/api/employees/2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Jane Doe"))
                .andExpect(jsonPath("$.department").value("HR"));
    }

    @Test
    public void testGetEmployeeById_NotFound() throws Exception {
        Mockito.when(employeeRepository.findById(99L)).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/employees/99"))
                .andExpect(status().isNotFound());
    }

    @Test
    public void testCreateEmployee() throws Exception {
        Employee newEmp = new Employee("Mark", "mark@example.com", "IT");
        Employee savedEmp = new Employee("Mark", "mark@example.com", "IT");
        savedEmp.setId(3L);

        Mockito.when(employeeRepository.save(any(Employee.class))).thenReturn(savedEmp);

        mockMvc.perform(post("/api/employees")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(newEmp)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(3))
                .andExpect(jsonPath("$.name").value("Mark"));
    }
}
