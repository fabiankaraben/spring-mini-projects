package com.example.customvalidator.controller;

import com.example.customvalidator.dto.EmployeeRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * REST Controller for Employee Operations.
 */
@RestController
@RequestMapping("/employees")
public class EmployeeController {

    /**
     * Endpoint to register an Employee. It requires a valid JSON body because
     * of @Valid.
     * 
     * @param request the employee configuration details
     * @return Success message map
     */
    @PostMapping
    public ResponseEntity<Map<String, String>> createEmployee(@RequestBody @Valid EmployeeRequest request) {
        // Since we reach this block, the properties in request are perfectly valid.
        // We can just return success or save to DB.

        Map<String, String> response = Map.of(
                "message", "Employee created successfully!",
                "employeeCode", request.getCode(),
                "employeeName", request.getName());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }
}
