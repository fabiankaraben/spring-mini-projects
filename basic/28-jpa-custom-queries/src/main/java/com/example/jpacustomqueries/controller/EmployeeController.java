package com.example.jpacustomqueries.controller;

import com.example.jpacustomqueries.entity.Employee;
import com.example.jpacustomqueries.service.EmployeeService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;

/**
 * REST Controller exposing RESTful HTTP endpoints for Employee management.
 */
@RestController
@RequestMapping("/api/employees")
public class EmployeeController {

    private final EmployeeService service;

    public EmployeeController(EmployeeService service) {
        this.service = service;
    }

    /**
     * Create a new employee using HTTP POST.
     */
    @PostMapping
    public ResponseEntity<Employee> createEmployee(@RequestBody Employee employee) {
        Employee saved = service.saveEmployee(employee);
        return new ResponseEntity<>(saved, HttpStatus.CREATED);
    }

    /**
     * Retrieve all employees using HTTP GET.
     */
    @GetMapping
    public ResponseEntity<List<Employee>> getAll() {
        return ResponseEntity.ok(service.getAllEmployees());
    }

    /**
     * Fetch a specific employee by email limit 1.
     */
    @GetMapping("/search/email")
    public ResponseEntity<Employee> getByEmail(@RequestParam String email) {
        Optional<Employee> employeeOpt = service.getEmployeeByEmail(email);
        // Using map() to yield code 200 with object, or 404 (Not Found) if absent
        return employeeOpt.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Search for employees mapping a partial name string.
     */
    @GetMapping("/search/name")
    public ResponseEntity<List<Employee>> searchByName(@RequestParam String name) {
        return ResponseEntity.ok(service.searchByName(name));
    }

    /**
     * Get all employees matching a department name using path variable.
     */
    @GetMapping("/department/{deptName}")
    public ResponseEntity<List<Employee>> getByDepartment(@PathVariable String deptName) {
        return ResponseEntity.ok(service.getEmployeesByDepartment(deptName));
    }
}
