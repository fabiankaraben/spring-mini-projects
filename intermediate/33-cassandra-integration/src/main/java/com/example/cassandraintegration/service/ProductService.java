package com.example.cassandraintegration.service;

import com.example.cassandraintegration.domain.Product;
import com.example.cassandraintegration.dto.CreateProductRequest;
import com.example.cassandraintegration.dto.UpdateProductRequest;
import com.example.cassandraintegration.repository.ProductRepository;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Service layer for Product-related operations.
 *
 * <p>Encapsulates all business logic for the product catalog.
 * Delegates persistence to {@link ProductRepository}.</p>
 *
 * <p>Important Cassandra constraint: every query must include the partition key
 * ({@code category}). The service enforces this by requiring category in all
 * lookup methods.</p>
 */
@Service
public class ProductService {

    /** Repository for Cassandra CRUD and custom CQL queries. */
    private final ProductRepository productRepository;

    /** Constructor injection — promotes testability via Mockito. */
    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    /**
     * Creates a new product in the Cassandra products table.
     *
     * <p>A new UUID is generated server-side for the clustering key ({@code id}).
     * This ensures each product has a globally unique identifier even across
     * distributed Cassandra nodes.</p>
     *
     * @param request DTO with category, name, description, price, and stock
     * @return the persisted Product with its generated UUID
     */
    public Product createProduct(CreateProductRequest request) {
        // Generate a random UUID for the product's clustering key
        UUID productId = UUID.randomUUID();

        Product product = new Product(
                request.getCategory(),
                productId,
                request.getName(),
                request.getDescription(),
                request.getPrice(),
                request.getStock()
        );

        return productRepository.save(product);
    }

    /**
     * Returns all products belonging to a specific category.
     *
     * <p>This is the most efficient Cassandra query since {@code category}
     * is the partition key — no scatter/gather across nodes needed.</p>
     *
     * @param category the partition key to query
     * @return list of products in the category
     */
    public List<Product> findByCategory(String category) {
        return productRepository.findByCategory(category);
    }

    /**
     * Finds a single product by its composite primary key (category + UUID).
     *
     * @param category the partition key
     * @param id       the clustering key
     * @return Optional Product
     */
    public Optional<Product> findByCategoryAndId(String category, UUID id) {
        return productRepository.findByCategoryAndId(category, id);
    }

    /**
     * Returns products in a category whose price is at or below the given maximum.
     *
     * @param category the partition key
     * @param maxPrice upper price limit (inclusive)
     * @return list of matching products
     */
    public List<Product> findByPriceAtMost(String category, BigDecimal maxPrice) {
        return productRepository.findByCategoryAndPriceLessThanEqual(category, maxPrice);
    }

    /**
     * Returns products in a category with stock above the given threshold.
     *
     * @param category  the partition key
     * @param minStock  minimum stock (exclusive lower bound)
     * @return list of in-stock products
     */
    public List<Product> findInStock(String category, int minStock) {
        return productRepository.findByCategoryAndStockGreaterThan(category, minStock);
    }

    /**
     * Updates mutable fields of an existing product.
     *
     * <p>The primary key ({@code category} + {@code id}) cannot change after creation —
     * in Cassandra, updating a primary key column requires a delete + re-insert.
     * Here we read, merge, and save to produce an in-place update of non-key columns.</p>
     *
     * @param category the partition key of the product to update
     * @param id       the clustering key of the product to update
     * @param request  DTO with optional fields to update (null fields are ignored)
     * @return the updated Product
     * @throws IllegalArgumentException if no product with the given keys exists
     */
    public Product updateProduct(String category, UUID id, UpdateProductRequest request) {
        // Load the existing product — throws if not found
        Product existing = productRepository.findByCategoryAndId(category, id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Product not found: category=" + category + ", id=" + id));

        // Apply partial updates: only override fields provided in the request
        if (request.getName() != null) {
            existing.setName(request.getName());
        }
        if (request.getDescription() != null) {
            existing.setDescription(request.getDescription());
        }
        if (request.getPrice() != null) {
            existing.setPrice(request.getPrice());
        }
        if (request.getStock() != null) {
            existing.setStock(request.getStock());
        }

        // Cassandra save() performs an upsert (INSERT OR REPLACE at the CQL level)
        return productRepository.save(existing);
    }

    /**
     * Deletes a product by its composite primary key.
     *
     * <p>In Cassandra, deletes write a "tombstone" marker. The data is not
     * physically removed until the next compaction (controlled by gc_grace_seconds).</p>
     *
     * @param category the partition key
     * @param id       the clustering key
     * @throws IllegalArgumentException if the product does not exist
     */
    public void deleteProduct(String category, UUID id) {
        Product existing = productRepository.findByCategoryAndId(category, id)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Product not found: category=" + category + ", id=" + id));
        productRepository.delete(existing);
    }
}
