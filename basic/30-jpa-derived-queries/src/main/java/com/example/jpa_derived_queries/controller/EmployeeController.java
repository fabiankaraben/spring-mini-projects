package com.example.jpa_derived_queries.controller;

import com.example.jpa_derived_queries.entity.Employee;
import com.example.jpa_derived_queries.service.EmployeeService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

/**
 * Controller to expose REST endpoints for our Employee entity queries.
 */
@RestController
@RequestMapping("/api/employees")
public class EmployeeController {

    private final EmployeeService employeeService;

    public EmployeeController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    // Example 1: GET /api/employees/department/Engineering
    @GetMapping("/department/{department}")
    public List<Employee> getByDepartment(@PathVariable String department) {
        return employeeService.getEmployeesByDepartment(department);
    }

    // Example 2: GET /api/employees/search?firstName=Alice&lastName=Smith
    @GetMapping("/search")
    public ResponseEntity<Employee> getByName(@RequestParam String firstName, @RequestParam String lastName) {
        return employeeService.getEmployeeByName(firstName, lastName)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // Example 3: GET /api/employees/salary-greater-than/80000
    @GetMapping("/salary-greater-than/{salary}")
    public List<Employee> getBySalaryGreaterThan(@PathVariable Double salary) {
        return employeeService.getEmployeesWithSalaryGreaterThan(salary);
    }

    // Example 4: GET /api/employees/active
    @GetMapping("/active")
    public List<Employee> getActive() {
        return employeeService.getActiveEmployees();
    }

    // Example 5: GET /api/employees/hired-between?start=2021-01-01&end=2023-12-31
    @GetMapping("/hired-between")
    public List<Employee> getHiredBetween(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate end) {
        return employeeService.getEmployeesHiredBetween(start, end);
    }

    // Example 6: GET /api/employees/active-ordered
    @GetMapping("/active-ordered")
    public List<Employee> getActiveOrdered() {
        return employeeService.getActiveEmployeesOrderedBySalary();
    }

    // Example 7: GET /api/employees/starting-with/A
    @GetMapping("/starting-with/{prefix}")
    public List<Employee> getStartingWith(@PathVariable String prefix) {
        return employeeService.getEmployeesStartingWith(prefix);
    }
}
