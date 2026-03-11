package com.example.testcontainerspostgres.service;

import com.example.testcontainerspostgres.dto.ProductRequest;
import com.example.testcontainerspostgres.dto.ProductResponse;
import com.example.testcontainerspostgres.entity.Product;
import com.example.testcontainerspostgres.exception.ProductNotFoundException;
import com.example.testcontainerspostgres.repository.ProductRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;

/**
 * Service layer for {@link Product} business logic.
 *
 * <p>The service layer sits between the controller (HTTP concerns) and the
 * repository (persistence concerns). Its responsibilities are:
 * <ul>
 *   <li>Orchestrate calls to the repository.</li>
 *   <li>Apply business rules (e.g. validate stock before decrement).</li>
 *   <li>Map entities to/from DTOs so the controller never touches entities.</li>
 *   <li>Manage transaction boundaries via {@code @Transactional}.</li>
 * </ul>
 *
 * <p>This class is the primary target of the <b>unit tests</b> in
 * {@code ProductServiceTest}. The repository is mocked with Mockito so
 * no database or Spring context is needed for those tests — they run fast
 * and in pure isolation. The integration tests ({@code ProductRepositoryIntegrationTest})
 * then verify the repository queries against a real PostgreSQL container.
 */
@Service
@Transactional(readOnly = true) // default: read-only transactions for all methods
public class ProductService {

    /**
     * The repository used to access and persist {@link Product} entities.
     * Injected via constructor injection (preferred over field injection).
     */
    private final ProductRepository productRepository;

    /**
     * Constructor injection — the recommended style in Spring Boot.
     * Single-constructor classes do not need {@code @Autowired}; Spring detects
     * the constructor automatically.
     *
     * @param productRepository the JPA repository for products
     */
    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    // =========================================================================
    // Read operations (readOnly = true inherited from class-level @Transactional)
    // =========================================================================

    /**
     * Returns all products in the database.
     *
     * @return list of all products as response DTOs; empty list if none exist
     */
    public List<ProductResponse> findAll() {
        // Stream each entity through the DTO factory method and collect to a list
        return productRepository.findAll()
                .stream()
                .map(ProductResponse::from)
                .toList();
    }

    /**
     * Returns a single product by its ID.
     *
     * @param id the product ID to look up
     * @return the matching product as a response DTO
     * @throws ProductNotFoundException if no product with that ID exists
     */
    public ProductResponse findById(Long id) {
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));
        return ProductResponse.from(product);
    }

    /**
     * Returns all products whose names contain the given keyword (case-insensitive).
     *
     * @param keyword the search term to look for within product names
     * @return list of matching products as response DTOs; empty list if none match
     */
    public List<ProductResponse> search(String keyword) {
        return productRepository.findByNameContainingIgnoreCase(keyword)
                .stream()
                .map(ProductResponse::from)
                .toList();
    }

    /**
     * Returns all products currently in stock (stockQuantity &gt; 0).
     *
     * @return list of in-stock products as response DTOs; empty list if all are out of stock
     */
    public List<ProductResponse> findInStock() {
        return productRepository.findAllInStock()
                .stream()
                .map(ProductResponse::from)
                .toList();
    }

    /**
     * Returns all products whose price falls within the given range (inclusive).
     *
     * @param min the lower price bound (inclusive)
     * @param max the upper price bound (inclusive)
     * @return list of matching products as response DTOs; empty list if none match
     */
    public List<ProductResponse> findByPriceRange(BigDecimal min, BigDecimal max) {
        return productRepository.findByPriceBetween(min, max)
                .stream()
                .map(ProductResponse::from)
                .toList();
    }

    // =========================================================================
    // Write operations (override readOnly with @Transactional for full transactions)
    // =========================================================================

    /**
     * Creates a new product from the given request data and persists it.
     *
     * <p>{@code @Transactional} on this method overrides the class-level
     * {@code readOnly = true}, enabling a full read-write transaction for the INSERT.
     *
     * @param request the validated request DTO from the controller
     * @return the newly created product as a response DTO (with its generated ID)
     */
    @Transactional
    public ProductResponse create(ProductRequest request) {
        // Map the DTO to an entity and persist it
        Product product = new Product(
                request.name(),
                request.description(),
                request.price(),
                request.stockQuantity()
        );
        Product saved = productRepository.save(product);
        return ProductResponse.from(saved);
    }

    /**
     * Updates an existing product's fields from the given request data.
     *
     * <p>Only the fields present in the request are updated. The ID is taken
     * from the path variable, not from the request body, to prevent clients
     * from changing the ID.
     *
     * @param id      the ID of the product to update
     * @param request the validated update request DTO
     * @return the updated product as a response DTO
     * @throws ProductNotFoundException if no product with the given ID exists
     */
    @Transactional
    public ProductResponse update(Long id, ProductRequest request) {
        // Load the existing entity — throws if not found
        Product product = productRepository.findById(id)
                .orElseThrow(() -> new ProductNotFoundException(id));

        // Update the mutable fields
        product.setName(request.name());
        product.setDescription(request.description());
        product.setPrice(request.price());
        product.setStockQuantity(request.stockQuantity());

        // save() here issues an UPDATE; the returned entity is the persisted state
        Product updated = productRepository.save(product);
        return ProductResponse.from(updated);
    }

    /**
     * Deletes the product with the given ID.
     *
     * @param id the ID of the product to delete
     * @throws ProductNotFoundException if no product with the given ID exists
     */
    @Transactional
    public void delete(Long id) {
        // Check existence before deleting so we can throw a descriptive 404
        if (!productRepository.existsById(id)) {
            throw new ProductNotFoundException(id);
        }
        productRepository.deleteById(id);
    }
}
