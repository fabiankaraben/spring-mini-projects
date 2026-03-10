package com.example.optimisticlocking.service;

import com.example.optimisticlocking.domain.Product;
import com.example.optimisticlocking.dto.ProductRequest;
import com.example.optimisticlocking.dto.ProductUpdateRequest;
import com.example.optimisticlocking.exception.ProductNotFoundException;
import com.example.optimisticlocking.repository.ProductRepository;
import jakarta.persistence.EntityManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service layer for {@link Product} CRUD operations with optimistic locking support.
 *
 * <h2>Optimistic Locking Flow</h2>
 * <p>The {@link #update(Long, ProductUpdateRequest)} method is the core demonstration
 * of optimistic locking in this project.  The flow is:</p>
 * <ol>
 *   <li>The client fetches a product via GET and receives the current {@code version}.</li>
 *   <li>The client includes that {@code version} in the PUT request body.</li>
 *   <li>This service loads the entity from the database, sets the client-supplied
 *       version on it, applies the field changes, and calls {@code save()}.</li>
 *   <li>Hibernate generates:
 *       {@code UPDATE products SET name=?, ..., version=N+1 WHERE id=? AND version=N}</li>
 *   <li>If another concurrent request already incremented the version, the WHERE clause
 *       matches zero rows and Hibernate throws
 *       {@link org.springframework.orm.ObjectOptimisticLockingFailureException}.</li>
 *   <li>The {@link com.example.optimisticlocking.exception.GlobalExceptionHandler} catches
 *       that exception and returns HTTP 409 Conflict to the caller.</li>
 * </ol>
 *
 * <h2>Transaction handling</h2>
 * <p>The class is annotated {@code @Transactional(readOnly = true)} as a safe default.
 * Methods that write to the database override this with a plain {@code @Transactional}.</p>
 */
@Service
@Transactional(readOnly = true) // default to read-only; override on mutating methods
public class ProductService {

    private final ProductRepository productRepository;

    /**
     * The JPA {@link EntityManager} is needed to call {@link EntityManager#detach(Object)}
     * in the {@link #update} method.  Detaching the entity before setting the client-supplied
     * version ensures Hibernate treats the subsequent {@code save()} as a merge of a
     * <em>detached</em> entity, which forces it to use the version value we set rather than
     * the version it captured in the persistence-context snapshot at load time.
     */
    private final EntityManager entityManager;

    /**
     * Constructor injection – preferred over field injection for testability.
     *
     * @param productRepository Spring Data repository for product persistence
     * @param entityManager     JPA entity manager (needed for detach in update)
     */
    public ProductService(ProductRepository productRepository, EntityManager entityManager) {
        this.productRepository = productRepository;
        this.entityManager = entityManager;
    }

    // ── Read operations ───────────────────────────────────────────────────────

    /**
     * Returns all products.
     *
     * @return list of all products (may be empty but never {@code null})
     */
    public List<Product> findAll() {
        return productRepository.findAll();
    }

    /**
     * Finds a product by its primary key.
     *
     * @param id the product's primary key
     * @return the matching product
     * @throws ProductNotFoundException if no product with that ID exists
     */
    public Product findById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
    }

    /**
     * Searches products by a case-insensitive name fragment.
     *
     * @param name the substring to look for (empty string returns all)
     * @return list of matching products
     */
    public List<Product> searchByName(String name) {
        return productRepository.findByNameContainingIgnoreCase(name);
    }

    /**
     * Lists all products in a given category.
     *
     * @param category the category to filter by
     * @return list of products in that category
     */
    public List<Product> findByCategory(String category) {
        return productRepository.findByCategory(category);
    }

    // ── Write operations ──────────────────────────────────────────────────────

    /**
     * Creates a new product from the given request DTO.
     *
     * <p>The {@code version} field is initialised to {@code 0} automatically by
     * Hibernate when the INSERT is executed, because the column is annotated with
     * {@code @Version}.</p>
     *
     * @param request validated product data
     * @return the persisted product (with auto-generated ID, version=0, and timestamps)
     */
    @Transactional
    public Product create(ProductRequest request) {
        // Map DTO → entity; @PrePersist sets createdAt/updatedAt; @Version sets version=0
        Product product = new Product(
                request.name(),
                request.description(),
                request.price(),
                request.stock(),
                request.category()
        );
        return productRepository.save(product);
    }

    /**
     * Updates an existing product, enforcing optimistic locking via the client-supplied version.
     *
     * <h2>Step-by-step</h2>
     * <ol>
     *   <li>Load the current entity from the database (this sets the Hibernate-managed
     *       version in the persistence context).</li>
     *   <li>Override the managed version with the client-supplied {@code request.version()}.
     *       This is the key step: if the client's version is stale (lower than the DB value),
     *       Hibernate's WHERE clause will not match any row.</li>
     *   <li>Apply the field changes to the managed entity.</li>
     *   <li>Call {@code save()} – Hibernate flushes the dirty entity:
     *       {@code UPDATE products SET name=?, ..., version=N+1 WHERE id=? AND version=N}</li>
     *   <li>If zero rows are updated → Hibernate throws
     *       {@link org.springframework.orm.ObjectOptimisticLockingFailureException}
     *       → bubbles up to the controller → global handler returns HTTP 409.</li>
     * </ol>
     *
     * @param id      the product's primary key
     * @param request new field values including the current version
     * @return the updated product with an incremented version
     * @throws ProductNotFoundException if no product with that ID exists
     * @throws org.springframework.orm.ObjectOptimisticLockingFailureException if the
     *         version in the request does not match the current version in the database
     */
    @Transactional
    public Product update(Long id, ProductUpdateRequest request) {
        // Step 1: Load the entity – throws ProductNotFoundException if absent.
        //         At this point the entity is MANAGED inside the current persistence context,
        //         and Hibernate has recorded its current version in the context snapshot.
        Product product = findById(id);

        // Step 2: DETACH the entity from the persistence context.
        //         This is the critical step.  While the entity is managed, Hibernate ignores
        //         any manual changes to the @Version field (it uses its own snapshot for the
        //         WHERE clause).  By detaching we make the entity "detached", so the
        //         subsequent save() will go through EntityManager.merge(), which DOES honour
        //         the version field we are about to set.
        entityManager.detach(product);

        // Step 3: Override the version with the client-supplied value.
        //         Now that the entity is detached, this value will be used in the WHERE clause:
        //         UPDATE products SET ... WHERE id=? AND version=<this value>
        //         If the DB already has a higher version, 0 rows are updated → 409 Conflict.
        product.setVersion(request.version());

        // Step 4: Apply the new field values
        product.setName(request.name());
        product.setDescription(request.description());
        product.setPrice(request.price());
        product.setStock(request.stock());
        product.setCategory(request.category());

        // Step 5: save() on a detached entity calls EntityManager.merge().
        //         Hibernate generates:
        //         UPDATE products SET name=?, ..., version=N+1 WHERE id=? AND version=N
        //         If version=N no longer matches the DB (stale), 0 rows are updated →
        //         ObjectOptimisticLockingFailureException is thrown.
        return productRepository.save(product);
    }

    /**
     * Deletes a product by its ID.
     *
     * @param id the product's primary key
     * @throws ProductNotFoundException if no product with that ID exists
     */
    @Transactional
    public void delete(Long id) {
        // Verify the product exists before attempting deletion
        findById(id);
        productRepository.deleteById(id);
    }
}
