package com.example.jpa_derived_queries.controller;

import com.example.jpa_derived_queries.entity.Employee;
import com.example.jpa_derived_queries.service.EmployeeService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Controller test focusing only on the web layer.
 */
@WebMvcTest(EmployeeController.class)
class EmployeeControllerTest {

        @Autowired
        private MockMvc mockMvc;

        @MockitoBean
        private EmployeeService employeeService;

        @Test
        void getByDepartment_shouldReturnEmployees() throws Exception {
                Employee emp1 = new Employee("Alice", "Smith", "Engineering", 90000.0, LocalDate.now(), true);
                when(employeeService.getEmployeesByDepartment("Engineering"))
                                .thenReturn(List.of(emp1));

                mockMvc.perform(get("/api/employees/department/Engineering"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$[0].firstName").value("Alice"))
                                .andExpect(jsonPath("$[0].department").value("Engineering"));
        }

        @Test
        void getByName_shouldReturnEmployee_whenFound() throws Exception {
                Employee emp = new Employee("Alice", "Smith", "Engineering", 90000.0, LocalDate.now(), true);
                when(employeeService.getEmployeeByName("Alice", "Smith"))
                                .thenReturn(Optional.of(emp));

                mockMvc.perform(get("/api/employees/search?firstName=Alice&lastName=Smith"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$.firstName").value("Alice"))
                                .andExpect(jsonPath("$.lastName").value("Smith"));
        }

        @Test
        void getByName_shouldReturnNotFound_whenNotFound() throws Exception {
                when(employeeService.getEmployeeByName("Bob", "DoesntExist"))
                                .thenReturn(Optional.empty());

                mockMvc.perform(get("/api/employees/search?firstName=Bob&lastName=DoesntExist"))
                                .andExpect(status().isNotFound());
        }

        @Test
        void getHiredBetween_shouldReturnEmployees() throws Exception {
                Employee emp = new Employee("Anna", "Zimmerman", "HR", 70000.0, LocalDate.of(2022, 5, 5), true);
                when(employeeService.getEmployeesHiredBetween(any(LocalDate.class), any(LocalDate.class)))
                                .thenReturn(List.of(emp));

                mockMvc.perform(get("/api/employees/hired-between?start=2022-01-01&end=2022-12-31"))
                                .andExpect(status().isOk())
                                .andExpect(jsonPath("$[0].firstName").value("Anna"));
        }
}
