package com.example.h2_database_setup.repository;

import com.example.h2_database_setup.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository interface for managing Product entities in the database.
 * Spring Data JPA creates an implementation at runtime.
 */
@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {
    List<Product> findByNameContainingIgnoreCase(String name);
}
