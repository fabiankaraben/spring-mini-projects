package com.example.jpapagination.controller;

import com.example.jpapagination.model.Employee;
import com.example.jpapagination.service.EmployeeService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * Controller exposing endpoints for paginating Employees.
 */
@RestController
@RequestMapping("/api/employees")
public class EmployeeController {

    private final EmployeeService employeeService;

    public EmployeeController(EmployeeService employeeService) {
        this.employeeService = employeeService;
    }

    /**
     * Endpoint to get a paginated list of all employees.
     * Use query parameters: ?page=0&size=10&sort=id,asc
     * 
     * @PageableDefault allows setting default values in case no parameters are
     *                  passed.
     */
    @GetMapping
    public Page<Employee> getAllEmployees(
            @PageableDefault(page = 0, size = 10, sort = "id", direction = Sort.Direction.ASC) Pageable pageable) {
        // Return a JSON representation of a Page containing content and metadata
        return employeeService.getAllEmployees(pageable);
    }

    /**
     * Endpoint to get a paginated list of employees by their department.
     * Example: /api/employees/department?department=IT&page=0&size=5
     */
    @GetMapping("/department")
    public Page<Employee> getEmployeesByDepartment(
            @RequestParam String department,
            @PageableDefault(page = 0, size = 5, sort = "name", direction = Sort.Direction.ASC) Pageable pageable) {
        return employeeService.getEmployeesByDepartment(department, pageable);
    }
}
