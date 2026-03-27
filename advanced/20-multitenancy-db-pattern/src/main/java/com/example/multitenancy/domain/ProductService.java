package com.example.multitenancy.domain;

import com.example.multitenancy.web.dto.ProductRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Service layer for {@link Product} domain operations.
 *
 * <p>This class contains the business logic for managing products. It coordinates
 * between the REST controller and the JPA repository, applies transactional
 * boundaries, and enforces business rules (e.g., validating that a product being
 * updated belongs to the correct tenant — which is implicit in this design because
 * the repository already scopes all queries to the current tenant's schema).</p>
 *
 * <h2>Transaction management</h2>
 * <p>The {@code @Transactional} annotation ensures that:</p>
 * <ul>
 *   <li>All database operations within a service method participate in the same
 *       transaction.</li>
 *   <li>If any exception is thrown, the entire transaction is rolled back.</li>
 *   <li>The {@code readOnly = true} hint on query methods tells Spring/Hibernate to
 *       skip dirty-checking, improving performance for read-heavy workloads.</li>
 * </ul>
 */
@Service
@Transactional(readOnly = true)
public class ProductService {

    private final ProductRepository productRepository;

    /**
     * Constructs the service with its repository dependency.
     *
     * @param productRepository the JPA repository for products
     */
    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    /**
     * Returns all products for the current tenant.
     *
     * <p>Because the repository is scoped to the tenant's schema (via the Hibernate
     * multi-tenancy setup), this returns only the products belonging to the tenant
     * identified in the {@code X-Tenant-ID} request header.</p>
     *
     * @return a list of all products, or an empty list if none exist
     */
    public List<Product> findAll() {
        return productRepository.findAll();
    }

    /**
     * Searches for products whose name contains the given keyword (case-insensitive).
     *
     * @param keyword the substring to search for
     * @return a list of matching products, or an empty list if none found
     */
    public List<Product> search(String keyword) {
        return productRepository.findByNameContainingIgnoreCase(keyword);
    }

    /**
     * Retrieves a single product by its ID within the current tenant's schema.
     *
     * @param id the product's primary key
     * @return an {@link Optional} containing the product, or empty if not found
     */
    public Optional<Product> findById(Long id) {
        return productRepository.findById(id);
    }

    /**
     * Creates a new product for the current tenant.
     *
     * <p>The product is saved to the current tenant's schema. The {@code @Transactional}
     * annotation (inherited from the class-level annotation, overridden here to allow
     * writes) ensures the INSERT is committed when the method returns successfully.</p>
     *
     * @param request the DTO containing the new product's data
     * @return the persisted product with its generated ID and timestamps set
     */
    @Transactional
    public Product create(ProductRequest request) {
        Product product = new Product(
                request.name(),
                request.description(),
                request.price(),
                request.stockQuantity()
        );
        return productRepository.save(product);
    }

    /**
     * Updates an existing product for the current tenant.
     *
     * <p>Because the repository is scoped to the tenant's schema, looking up a
     * product by ID is implicitly tenant-safe: a tenant cannot update a product
     * in another tenant's schema, even if they guess the ID.</p>
     *
     * @param id      the ID of the product to update
     * @param request the DTO containing the updated product data
     * @return an {@link Optional} with the updated product, or empty if not found
     */
    @Transactional
    public Optional<Product> update(Long id, ProductRequest request) {
        return productRepository.findById(id).map(product -> {
            product.setName(request.name());
            product.setDescription(request.description());
            product.setPrice(request.price());
            product.setStockQuantity(request.stockQuantity());
            // save() is redundant here because the entity is already managed
            // (within a transaction), but it makes the intent explicit.
            return productRepository.save(product);
        });
    }

    /**
     * Deletes a product by its ID within the current tenant's schema.
     *
     * @param id the ID of the product to delete
     * @return {@code true} if the product existed and was deleted,
     *         {@code false} if it was not found
     */
    @Transactional
    public boolean delete(Long id) {
        if (productRepository.existsById(id)) {
            productRepository.deleteById(id);
            return true;
        }
        return false;
    }
}
