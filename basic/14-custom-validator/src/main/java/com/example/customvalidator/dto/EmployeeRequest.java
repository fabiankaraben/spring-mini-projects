package com.example.customvalidator.dto;

import com.example.customvalidator.annotation.ValidEmployeeCode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Data Transfer Object representing an Employee creation request.
 */
public class EmployeeRequest {

    /**
     * Standard built-in Spring Validation.
     */
    @NotBlank(message = "Name cannot be blank.")
    @Size(min = 2, max = 50, message = "Name must be between 2 and 50 characters.")
    private String name;

    /**
     * Our Custom Bean Validator applied.
     */
    @ValidEmployeeCode
    private String code;

    // Constructors
    public EmployeeRequest() {
    }

    public EmployeeRequest(String name, String code) {
        this.name = name;
        this.code = code;
    }

    // Getters and Setters
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }
}
