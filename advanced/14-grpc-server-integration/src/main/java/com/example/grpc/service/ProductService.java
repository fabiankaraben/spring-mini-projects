package com.example.grpc.service;

import com.example.grpc.domain.Product;
import com.example.grpc.domain.ProductStatus;
import com.example.grpc.repository.ProductRepository;
import jakarta.validation.Valid;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

/**
 * Service layer for Product Catalog domain logic.
 *
 * <p>This class encapsulates all business rules for product management.
 * It is intentionally decoupled from gRPC — it operates exclusively on JPA
 * {@link Product} entities and Java primitives, with no dependency on protobuf types.
 *
 * <p>Why separate the service from the gRPC layer?
 * <ul>
 *   <li>Business logic can be unit-tested without a running gRPC server or Spring context.</li>
 *   <li>The same service could be reused by a REST controller, a message consumer, etc.</li>
 *   <li>Clear separation of concerns: gRPC layer handles protocol, service handles domain.</li>
 * </ul>
 *
 * <p>Transaction management:
 *   {@code @Transactional} on write methods ensures that all DB operations within a
 *   method are either fully committed or fully rolled back on exception.
 *   Read methods are annotated with {@code readOnly = true} to allow the JPA provider
 *   to apply optimizations (e.g., skip dirty-checking on read-only transactions).
 */
@Service
@Transactional(readOnly = true)
public class ProductService {

    /**
     * Spring Data JPA repository for CRUD and query operations on products.
     * Injected by Spring via constructor injection (preferred over field injection).
     */
    private final ProductRepository productRepository;

    /**
     * Constructor injection — ensures the dependency is required and immutable.
     *
     * @param productRepository the JPA repository for products
     */
    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    // -------------------------------------------------------------------------
    // Read operations
    // -------------------------------------------------------------------------

    /**
     * Retrieve a single product by its numeric ID.
     *
     * @param id the product's primary key
     * @return an {@link Optional} containing the product if found, or empty if not found
     */
    public Optional<Product> findById(Long id) {
        return productRepository.findById(id);
    }

    /**
     * Retrieve all non-discontinued products, optionally filtered by category.
     *
     * <p>DISCONTINUED products are excluded from normal listings — they represent
     * soft-deleted records retained in the database for audit purposes.
     *
     * @param category optional category filter; {@code null} or blank means "all categories"
     * @return list of matching active/out-of-stock products, ordered by name
     */
    public List<Product> findAll(String category) {
        if (category != null && !category.isBlank()) {
            // Return products of the given category that are not discontinued.
            return productRepository.findByCategoryAndStatusNotOrderByNameAsc(
                    category, ProductStatus.DISCONTINUED);
        }
        // Return all products that are not discontinued.
        return productRepository.findByStatusNotOrderByNameAsc(ProductStatus.DISCONTINUED);
    }

    // -------------------------------------------------------------------------
    // Write operations
    // -------------------------------------------------------------------------

    /**
     * Create a new product and persist it to the database.
     *
     * <p>Business rules applied here:
     * <ul>
     *   <li>New products start with status {@code ACTIVE} if stock > 0,
     *       or {@code OUT_OF_STOCK} if the initial stock is 0.</li>
     * </ul>
     *
     * @param name          product name (must not be blank)
     * @param description   product description
     * @param category      product category
     * @param price         unit price in USD (must be > 0)
     * @param stockQuantity initial stock count (must be >= 0)
     * @return the persisted {@link Product} entity with its generated ID
     */
    @Transactional
    public Product createProduct(String name, String description, String category,
                                  Double price, Integer stockQuantity) {
        // Determine initial status based on the provided stock quantity.
        ProductStatus initialStatus = (stockQuantity > 0)
                ? ProductStatus.ACTIVE
                : ProductStatus.OUT_OF_STOCK;

        Product product = new Product(name, description, category, price, stockQuantity, initialStatus);
        return productRepository.save(product);
    }

    /**
     * Update the stock quantity of an existing product.
     *
     * <p>Business rules applied here:
     * <ul>
     *   <li>If the new quantity is 0, the status changes to {@code OUT_OF_STOCK}.</li>
     *   <li>If the new quantity is > 0 and the current status is {@code OUT_OF_STOCK},
     *       the status is automatically restored to {@code ACTIVE}.</li>
     *   <li>If the product is {@code DISCONTINUED}, its status is NOT changed —
     *       discontinued products cannot be restocked.</li>
     * </ul>
     *
     * @param id               the ID of the product to update
     * @param newStockQuantity the new absolute stock quantity (must be >= 0)
     * @return an {@link Optional} containing the updated product, or empty if not found
     * @throws IllegalArgumentException if {@code newStockQuantity} is negative
     */
    @Transactional
    public Optional<Product> updateStock(Long id, Integer newStockQuantity) {
        if (newStockQuantity < 0) {
            throw new IllegalArgumentException("Stock quantity cannot be negative: " + newStockQuantity);
        }

        return productRepository.findById(id).map(product -> {
            product.setStockQuantity(newStockQuantity);

            // Automatically manage status transitions based on stock level.
            if (newStockQuantity == 0) {
                // Product ran out of stock.
                if (product.getStatus() == ProductStatus.ACTIVE) {
                    product.setStatus(ProductStatus.OUT_OF_STOCK);
                }
            } else {
                // Stock is being replenished.
                if (product.getStatus() == ProductStatus.OUT_OF_STOCK) {
                    product.setStatus(ProductStatus.ACTIVE);
                }
                // Note: DISCONTINUED products remain DISCONTINUED even if stock > 0.
            }

            return productRepository.save(product);
        });
    }

    /**
     * Soft-delete a product by setting its status to {@code DISCONTINUED}.
     *
     * <p>Why soft-delete?
     *   Hard-deleting a product breaks referential integrity if other systems (e.g.,
     *   order history) reference it by ID. Soft-delete keeps the record but marks it
     *   invisible to normal queries.
     *
     * @param id the ID of the product to delete
     * @return {@code true} if the product was found and marked DISCONTINUED,
     *         {@code false} if no product with the given ID exists
     */
    @Transactional
    public boolean deleteProduct(Long id) {
        return productRepository.findById(id).map(product -> {
            product.setStatus(ProductStatus.DISCONTINUED);
            productRepository.save(product);
            return true;
        }).orElse(false);
    }
}
