package com.example.flywaymigrations.controller;

import org.springframework.web.bind.annotation.*;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;

import com.example.flywaymigrations.entity.Employee;
import com.example.flywaymigrations.repository.EmployeeRepository;

import java.util.List;
import java.util.Optional;

/**
 * REST Controller to interact with the Employee entities.
 */
@RestController
@RequestMapping("/api/employees")
public class EmployeeController {

    private final EmployeeRepository repository;

    // Constructor-based dependency injection
    public EmployeeController(EmployeeRepository repository) {
        this.repository = repository;
    }

    /**
     * Get all employees.
     * 
     * @return List of all employees.
     */
    @GetMapping
    public List<Employee> getAllEmployees() {
        return repository.findAll();
    }

    /**
     * Get an employee by ID.
     * 
     * @param id The ID of the employee.
     * @return The employee or a 404 Not Found.
     */
    @GetMapping("/{id}")
    public ResponseEntity<Employee> getEmployeeById(@PathVariable Long id) {
        Optional<Employee> employee = repository.findById(id);
        return employee.map(ResponseEntity::ok)
                .orElseGet(() -> ResponseEntity.notFound().build());
    }

    /**
     * Create a new employee.
     * 
     * @param employee The employee data.
     * @return The saved employee with its generated ID.
     */
    @PostMapping
    public ResponseEntity<Employee> createEmployee(@RequestBody Employee employee) {
        Employee saved = repository.save(employee);
        return ResponseEntity.status(HttpStatus.CREATED).body(saved);
    }
}
