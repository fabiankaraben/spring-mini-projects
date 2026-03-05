package com.example.jpa_derived_queries.service;

import com.example.jpa_derived_queries.entity.Employee;
import com.example.jpa_derived_queries.repository.EmployeeRepository;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

/**
 * Service class for handling business logic related to Employees.
 */
@Service
public class EmployeeService {

    private final EmployeeRepository employeeRepository;

    public EmployeeService(EmployeeRepository employeeRepository) {
        this.employeeRepository = employeeRepository;
    }

    /**
     * Initializes the DB with some demo data if it's empty.
     */
    @jakarta.annotation.PostConstruct
    public void init() {
        if (employeeRepository.count() == 0) {
            employeeRepository.saveAll(List.of(
                    new Employee("Alice", "Smith", "Engineering", 90000.0, LocalDate.of(2021, 1, 15), true),
                    new Employee("Bob", "Jones", "Engineering", 80000.0, LocalDate.of(2022, 5, 20), true),
                    new Employee("Charlie", "Brown", "HR", 60000.0, LocalDate.of(2020, 8, 10), false),
                    new Employee("Diana", "Prince", "Marketing", 95000.0, LocalDate.of(2019, 11, 1), true),
                    new Employee("Eve", "Adams", "Engineering", 110000.0, LocalDate.of(2023, 2, 28), true)));
        }
    }

    public List<Employee> getEmployeesByDepartment(String department) {
        return employeeRepository.findByDepartment(department);
    }

    public Optional<Employee> getEmployeeByName(String firstName, String lastName) {
        return employeeRepository.findByFirstNameAndLastName(firstName, lastName);
    }

    public List<Employee> getEmployeesWithSalaryGreaterThan(Double salary) {
        return employeeRepository.findBySalaryGreaterThan(salary);
    }

    public List<Employee> getActiveEmployees() {
        return employeeRepository.findByActiveTrue();
    }

    public List<Employee> getEmployeesHiredBetween(LocalDate startDate, LocalDate endDate) {
        return employeeRepository.findByHireDateBetween(startDate, endDate);
    }

    public List<Employee> getActiveEmployeesOrderedBySalary() {
        return employeeRepository.findByActiveTrueOrderBySalaryDesc();
    }

    public List<Employee> getEmployeesStartingWith(String prefix) {
        return employeeRepository.findByFirstNameStartingWith(prefix);
    }
}
