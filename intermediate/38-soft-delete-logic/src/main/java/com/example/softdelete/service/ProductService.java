package com.example.softdelete.service;

import com.example.softdelete.domain.Product;
import com.example.softdelete.dto.ProductRequest;
import com.example.softdelete.exception.ProductNotFoundException;
import com.example.softdelete.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service layer for {@link Product} CRUD operations, including soft-delete logic.
 *
 * <h2>Soft-delete transparency</h2>
 * <p>All "normal" read and write operations in this service work exclusively with
 * <em>active</em> (non-deleted) products.  The transparency is achieved entirely
 * through the {@code @SQLRestriction("deleted = false")} annotation on the entity:
 * no extra filtering is needed in this service.</p>
 *
 * <h2>Delete vs. hard delete</h2>
 * <p>Calling {@link #delete(Long)} does <em>not</em> remove the row from the database.
 * It invokes the JPA {@code deleteById} method, which triggers the custom SQL defined
 * in {@code @SQLDelete} on the entity – issuing an {@code UPDATE} that sets
 * {@code deleted = true}.  The row survives in the database and can be inspected via
 * the admin endpoints.</p>
 */
@Service
@Transactional(readOnly = true) // default to read-only; override on mutating methods
public class ProductService {

    private final ProductRepository productRepository;

    /**
     * Constructor injection – preferred over field injection for testability.
     *
     * @param productRepository Spring Data repository for product persistence
     */
    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    // ── Read operations ───────────────────────────────────────────────────────

    /**
     * Returns all active (non-deleted) products.
     *
     * <p>The {@code @SQLRestriction} on {@link Product} automatically filters out
     * soft-deleted rows, so this method can call {@code findAll()} without any
     * additional conditions.</p>
     *
     * @return list of all active products (may be empty but never {@code null})
     */
    public List<Product> findAll() {
        return productRepository.findAll();
    }

    /**
     * Finds an active product by its surrogate ID.
     *
     * <p>Because of the {@code @SQLRestriction}, a soft-deleted product with the same
     * ID will <em>not</em> be returned here – it is treated as if it does not exist.</p>
     *
     * @param id the product's primary key
     * @return the matching active product
     * @throws ProductNotFoundException if no active product with that ID exists
     */
    public Product findById(Long id) {
        return productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
    }

    /**
     * Searches active products by a case-insensitive name fragment.
     *
     * @param name the substring to look for (empty string returns all)
     * @return list of matching active products
     */
    public List<Product> searchByName(String name) {
        return productRepository.findByNameContainingIgnoreCase(name);
    }

    /**
     * Lists all active products in a given category.
     *
     * @param category the category to filter by
     * @return list of active products in that category
     */
    public List<Product> findByCategory(String category) {
        return productRepository.findByCategory(category);
    }

    /**
     * Returns all soft-deleted products (admin/audit view).
     *
     * <p>Uses the native SQL query in {@link ProductRepository#findAllDeleted()} to
     * bypass the {@code @SQLRestriction} filter.</p>
     *
     * @return list of soft-deleted products
     */
    public List<Product> findAllDeleted() {
        return productRepository.findAllDeleted();
    }

    /**
     * Returns the count of soft-deleted products (admin/audit view).
     *
     * @return number of rows where {@code deleted = true}
     */
    public long countDeleted() {
        return productRepository.countDeleted();
    }

    // ── Write operations ──────────────────────────────────────────────────────

    /**
     * Creates a new active product from the given request DTO.
     *
     * @param request validated product data
     * @return the persisted product (with auto-generated ID and timestamps)
     */
    @Transactional
    public Product create(ProductRequest request) {
        // Map DTO → entity; the entity's @PrePersist will set createdAt/updatedAt
        Product product = new Product(
                request.name(),
                request.description(),
                request.price(),
                request.category()
        );
        return productRepository.save(product);
    }

    /**
     * Updates the mutable fields of an existing active product.
     *
     * <p>Only active products can be updated.  Attempting to update a soft-deleted
     * product will raise {@link ProductNotFoundException} because the repository
     * cannot find it (filtered out by {@code @SQLRestriction}).</p>
     *
     * @param id      the product's primary key
     * @param request new field values
     * @return the updated and persisted product
     * @throws ProductNotFoundException if no active product with that ID exists
     */
    @Transactional
    public Product update(Long id, ProductRequest request) {
        Product product = findById(id); // throws ProductNotFoundException if absent

        // Update the mutable fields; @PreUpdate will set updatedAt
        product.setName(request.name());
        product.setDescription(request.description());
        product.setPrice(request.price());
        product.setCategory(request.category());

        return productRepository.save(product);
    }

    /**
     * Soft-deletes an active product by its ID.
     *
     * <h2>How the soft delete happens</h2>
     * <p>This method calls {@code productRepository.deleteById(id)}, which tells
     * Hibernate to execute the SQL from the entity's {@code @SQLDelete} annotation:
     * <pre>{@code
     * UPDATE products SET deleted = true, deleted_at = NOW() WHERE id = ?
     * }</pre>
     * No row is physically removed from the database.  After this call, the product
     * will be invisible to all normal (non-native) queries due to the
     * {@code @SQLRestriction("deleted = false")} filter.</p>
     *
     * @param id the product's primary key
     * @throws ProductNotFoundException if no active product with that ID exists
     */
    @Transactional
    public void delete(Long id) {
        // First verify the product exists and is active (throws if not found)
        findById(id);
        // Triggers @SQLDelete → UPDATE products SET deleted = true, deleted_at = NOW() WHERE id = ?
        productRepository.deleteById(id);
    }

    /**
     * Restores a previously soft-deleted product by clearing its deletion flags.
     *
     * <h2>How the restore works</h2>
     * <ol>
     *   <li>A native SQL query ({@link ProductRepository#findDeletedById}) locates the
     *       soft-deleted row, bypassing the {@code @SQLRestriction} filter.</li>
     *   <li>If found, {@link ProductRepository#restoreById} executes a native
     *       {@code UPDATE products SET deleted = false, deleted_at = NULL WHERE id = ?}
     *       and clears the Hibernate first-level cache
     *       ({@code clearAutomatically = true} on {@code @Modifying}).</li>
     *   <li>Finally, {@link #findById} re-fetches the now-active product through the
     *       normal (filtered) query path to confirm the restore succeeded.</li>
     * </ol>
     *
     * @param id the product's primary key
     * @return the restored (now active) product
     * @throws ProductNotFoundException if no soft-deleted product with that ID exists
     */
    @Transactional
    public Product restore(Long id) {
        // Verify the product exists in the deleted state (bypasses @SQLRestriction)
        productRepository.findDeletedById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));

        // Flip deleted=false and clear deleted_at via a native @Modifying query.
        // This bypasses @SQLRestriction which would block a normal JPA save on a
        // deleted entity.  clearAutomatically=true evicts the stale entity from
        // the Hibernate session so the next findById returns fresh data.
        productRepository.restoreById(id);

        // Re-load the now-active product through the normal (filtered) query path
        return findById(id);
    }
}
