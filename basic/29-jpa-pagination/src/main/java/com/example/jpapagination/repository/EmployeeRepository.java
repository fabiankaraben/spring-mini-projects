package com.example.jpapagination.repository;

import com.example.jpapagination.model.Employee;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

/**
 * Repository interface for Employee entity.
 * JpaRepository extends PagingAndSortingRepository, which means we get
 * pagination out of the box.
 */
@Repository
public interface EmployeeRepository extends JpaRepository<Employee, Long> {

    // Spring Data JPA allows us to pass a Pageable parameter to any query method
    // to return a Page of results.
    Page<Employee> findByDepartment(String department, Pageable pageable);
}
