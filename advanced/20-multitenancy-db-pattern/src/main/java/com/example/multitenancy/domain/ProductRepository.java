package com.example.multitenancy.domain;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for the {@link Product} entity.
 *
 * <p>Spring Data automatically generates the implementation of this interface at
 * startup, providing standard CRUD operations ({@code findAll}, {@code findById},
 * {@code save}, {@code deleteById}, etc.) without any boilerplate code.</p>
 *
 * <h2>Multi-tenancy integration</h2>
 * <p>Every method on this repository is transparently tenant-scoped. When a
 * repository method is called, Spring Data JPA delegates to Hibernate which calls
 * {@link com.example.multitenancy.tenant.TenantIdentifierResolver} to get the
 * active tenant, then calls
 * {@link com.example.multitenancy.tenant.TenantConnectionProvider} to obtain a
 * connection with the correct PostgreSQL {@code search_path}. The SQL is then
 * executed against the right tenant schema automatically.</p>
 *
 * <p>From the application code's perspective, this repository is completely
 * unaware of multi-tenancy — the isolation is entirely handled by the Hibernate
 * SPI layer below.</p>
 */
@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    /**
     * Finds all products whose name contains the given string (case-insensitive).
     *
     * <p>Spring Data generates the query from the method name:
     * {@code SELECT * FROM products WHERE LOWER(name) LIKE LOWER('%keyword%')}.</p>
     *
     * <p>This is executed against the current tenant's schema, just like any
     * other repository method.</p>
     *
     * @param keyword the substring to search for in the product name
     * @return a list of matching products, or an empty list if none found
     */
    List<Product> findByNameContainingIgnoreCase(String keyword);
}
