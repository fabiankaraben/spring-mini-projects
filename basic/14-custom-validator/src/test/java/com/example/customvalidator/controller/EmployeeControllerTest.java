package com.example.customvalidator.controller;

import com.example.customvalidator.dto.EmployeeRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.hasEntry;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(EmployeeController.class)
class EmployeeControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Test
    @DisplayName("Should successfully create an employee")
    void shouldCreateEmployeeWhenValid() throws Exception {
        EmployeeRequest validRequest = new EmployeeRequest("John Doe", "EMP-1001");

        mockMvc.perform(post("/employees")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(validRequest)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.message").value("Employee created successfully!"))
                .andExpect(jsonPath("$.employeeCode").value("EMP-1001"))
                .andExpect(jsonPath("$.employeeName").value("John Doe"));
    }

    @Test
    @DisplayName("Should return 400 Bad Request if code is invalid")
    void shouldReturnBadRequestWhenCodeInvalid() throws Exception {
        EmployeeRequest invalidCodeReq = new EmployeeRequest("John Doe", "EMP-12");

        mockMvc.perform(post("/employees")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(invalidCodeReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.title").value("Bad Request"))
                .andExpect(jsonPath("$.detail").value("Validation failed for request."))
                .andExpect(jsonPath("$.['invalid-params']", hasEntry("code",
                        "Invalid employee code format. It must start with 'EMP-' followed by 4 digits (e.g., EMP-1234).")));
    }

    @Test
    @DisplayName("Should return 400 Bad Request if standard validations fail")
    void shouldReturnBadRequestWhenNameBlank() throws Exception {
        EmployeeRequest blankNameReq = new EmployeeRequest("", "EMP-1002");

        mockMvc.perform(post("/employees")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(blankNameReq)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.['invalid-params']",
                        hasEntry("name", "Name cannot be blank.")));
    }
}
