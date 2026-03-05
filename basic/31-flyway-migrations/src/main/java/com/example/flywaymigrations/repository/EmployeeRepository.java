package com.example.flywaymigrations.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import com.example.flywaymigrations.entity.Employee;

/**
 * Spring Data JPA Repository for the Employee entity.
 * Provides basic CRUD operations automatically.
 */
@Repository
public interface EmployeeRepository extends JpaRepository<Employee, Long> {
}
