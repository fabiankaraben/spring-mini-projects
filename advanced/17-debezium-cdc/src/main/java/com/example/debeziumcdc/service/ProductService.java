package com.example.debeziumcdc.service;

import com.example.debeziumcdc.domain.Product;
import com.example.debeziumcdc.repository.ProductRepository;
import jakarta.persistence.EntityNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Domain service for product catalogue management.
 *
 * <p>All writes go through this service, which delegates to
 * {@link ProductRepository} (backed by JPA / PostgreSQL).
 * Every committed database change (INSERT, UPDATE, DELETE) is automatically
 * captured by Debezium's embedded engine via the PostgreSQL WAL and streamed
 * to Kafka — no additional code is required here to trigger CDC.
 *
 * <p>Transaction boundaries:
 * <ul>
 *   <li>Write methods ({@link #createProduct}, {@link #updateProduct},
 *       {@link #deleteProduct}) are transactional so that Debezium always
 *       sees a fully committed record in the WAL.</li>
 *   <li>Read methods are annotated with {@code readOnly = true} for performance.</li>
 * </ul>
 */
@Service
public class ProductService {

    private static final Logger log = LoggerFactory.getLogger(ProductService.class);

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    // -------------------------------------------------------------------------
    // Create
    // -------------------------------------------------------------------------

    /**
     * Creates and persists a new product.
     *
     * <p>After the transaction commits, Debezium will capture an INSERT event
     * from the PostgreSQL WAL and emit a CREATE {@link com.example.debeziumcdc.cdc.ProductCdcEvent}.
     *
     * @param name        product display name (must not be blank)
     * @param description optional human-readable description
     * @param price       unit price (must be positive)
     * @param stock       initial stock level (must be non-negative)
     * @return the persisted product with its assigned database ID
     */
    @Transactional
    public Product createProduct(String name, String description, BigDecimal price, int stock) {
        Product product = new Product(name, description, price, stock);
        Product saved = productRepository.save(product);
        log.info("Product created: id={}, name='{}', price={}", saved.getId(), saved.getName(), saved.getPrice());
        return saved;
    }

    // -------------------------------------------------------------------------
    // Read
    // -------------------------------------------------------------------------

    /**
     * Retrieves a product by its ID.
     *
     * @param id the product primary key
     * @return an {@link Optional} containing the product, or empty if not found
     */
    @Transactional(readOnly = true)
    public Optional<Product> findById(Long id) {
        return productRepository.findById(id);
    }

    /**
     * Returns all products in the catalogue.
     *
     * @return list of all products, ordered by database insertion order
     */
    @Transactional(readOnly = true)
    public List<Product> findAll() {
        return productRepository.findAll();
    }

    /**
     * Searches for products whose name contains the given keyword (case-insensitive).
     *
     * @param keyword the search term
     * @return list of matching products
     */
    @Transactional(readOnly = true)
    public List<Product> searchByName(String keyword) {
        return productRepository.findByNameContainingIgnoreCase(keyword);
    }

    // -------------------------------------------------------------------------
    // Update
    // -------------------------------------------------------------------------

    /**
     * Updates an existing product's fields.
     *
     * <p>Only non-null parameters are applied; null values leave the
     * corresponding field unchanged. After the transaction commits, Debezium
     * captures an UPDATE event containing both the before and after states.
     *
     * @param id          ID of the product to update
     * @param name        new name (or null to keep current)
     * @param description new description (or null to keep current)
     * @param price       new price (or null to keep current)
     * @param stock       new stock level (or null to keep current)
     * @return the updated product
     * @throws EntityNotFoundException if no product exists with the given ID
     */
    @Transactional
    public Product updateProduct(Long id, String name, String description,
                                  BigDecimal price, Integer stock) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new EntityNotFoundException("Product not found: id=" + id));

        if (name        != null) product.setName(name);
        if (description != null) product.setDescription(description);
        if (price       != null) product.setPrice(price);
        if (stock       != null) product.setStock(stock);

        Product saved = productRepository.save(product);
        log.info("Product updated: id={}, name='{}', price={}, stock={}",
                saved.getId(), saved.getName(), saved.getPrice(), saved.getStock());
        return saved;
    }

    // -------------------------------------------------------------------------
    // Delete
    // -------------------------------------------------------------------------

    /**
     * Deletes a product by its ID.
     *
     * <p>After the transaction commits, Debezium captures a DELETE event
     * containing the before state (the deleted row's values).
     *
     * @param id the product ID to delete
     * @throws EntityNotFoundException if no product exists with the given ID
     */
    @Transactional
    public void deleteProduct(Long id) {
        if (!productRepository.existsById(id)) {
            throw new EntityNotFoundException("Product not found: id=" + id);
        }
        productRepository.deleteById(id);
        log.info("Product deleted: id={}", id);
    }
}
