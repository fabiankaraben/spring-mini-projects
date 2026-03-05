package com.example.jpacustomqueries.service;

import com.example.jpacustomqueries.entity.Employee;
import com.example.jpacustomqueries.repository.EmployeeRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Service layer acting as a bridge between the Controller and Repository.
 */
@Service
public class EmployeeService {

    private final EmployeeRepository repository;

    /**
     * Constructor injection for required dependencies.
     * Preferred over @Autowired annotation directly on fields.
     */
    public EmployeeService(EmployeeRepository repository) {
        this.repository = repository;
    }

    /**
     * Saves a new Employee record to the database.
     */
    public Employee saveEmployee(Employee employee) {
        return repository.save(employee);
    }

    /**
     * Retrieves all employees from the database.
     */
    public List<Employee> getAllEmployees() {
        return repository.findAll();
    }

    /**
     * Finds a single employee by an exact email match.
     * Calls a custom JPQL query.
     */
    public Optional<Employee> getEmployeeByEmail(String email) {
        return repository.findByEmailExactly(email);
    }

    /**
     * Finds employees given a substring of their name.
     * Calls a custom JPQL query.
     */
    public List<Employee> searchByName(String name) {
        return repository.findByNameContainingIgnoreCaseCustom(name);
    }

    /**
     * Returns employees belonging to a specified department.
     * Calls a custom Native Query.
     */
    public List<Employee> getEmployeesByDepartment(String dept) {
        return repository.findAllByDepartmentNative(dept);
    }
}
