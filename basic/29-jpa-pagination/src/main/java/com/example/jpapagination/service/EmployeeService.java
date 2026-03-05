package com.example.jpapagination.service;

import com.example.jpapagination.model.Employee;
import com.example.jpapagination.repository.EmployeeRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

/**
 * Service class for handling Employee business logic.
 */
@Service
public class EmployeeService {

    private final EmployeeRepository employeeRepository;

    public EmployeeService(EmployeeRepository employeeRepository) {
        this.employeeRepository = employeeRepository;
    }

    /**
     * Retrieves a paginated list of all employees.
     *
     * @param pageable The pagination information.
     * @return A page of Employee objects.
     */
    public Page<Employee> getAllEmployees(Pageable pageable) {
        // Find all records from the repository using the provided Pageable
        return employeeRepository.findAll(pageable);
    }

    /**
     * Retrieves a paginated list of employees by their department.
     *
     * @param department The department to filter by.
     * @param pageable   The pagination information.
     * @return A page of Employee objects filtered by department.
     */
    public Page<Employee> getEmployeesByDepartment(String department, Pageable pageable) {
        // Forward to the repository's custom parameterized method
        return employeeRepository.findByDepartment(department, pageable);
    }
}
