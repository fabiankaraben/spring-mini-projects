package com.example.mongodbcrudapi.service;

import com.example.mongodbcrudapi.domain.Product;
import com.example.mongodbcrudapi.dto.ProductRequest;
import com.example.mongodbcrudapi.repository.ProductRepository;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Service layer containing the business logic for product management.
 *
 * <p>This class acts as the intermediary between the HTTP layer ({@link com.example.mongodbcrudapi.controller.ProductController})
 * and the data access layer ({@link ProductRepository}). Separating business
 * logic into a dedicated service class provides several benefits:
 * <ul>
 *   <li>Testability: the service can be tested in isolation by mocking the
 *       repository (no MongoDB connection needed for unit tests).</li>
 *   <li>Single Responsibility: the controller handles HTTP concerns; the service
 *       handles business rules and data transformations.</li>
 *   <li>Reusability: multiple controllers or event listeners can share the same
 *       service without duplicating logic.</li>
 * </ul>
 *
 * <p>All MongoDB interactions are delegated to {@link ProductRepository}. The
 * service maps DTOs ({@link ProductRequest}) to domain objects ({@link Product})
 * before persisting, which decouples the API contract from the storage model.
 */
@Service
public class ProductService {

    private final ProductRepository productRepository;

    /**
     * {@link MongoTemplate} provides a lower-level API for building programmatic
     * queries with {@link Criteria}. It is used here for the price-range filter
     * because programmatic queries handle Java {@link BigDecimal} to MongoDB
     * Decimal128 BSON type conversion more reliably than derived repository methods
     * or JSON {@code @Query} strings across different MongoDB driver versions.
     */
    private final MongoTemplate mongoTemplate;

    /**
     * Constructor injection is preferred over {@code @Autowired} field injection
     * because it makes dependencies explicit, enforces immutability (final fields),
     * and simplifies unit testing (no Spring context needed — just pass a mock).
     *
     * @param productRepository the MongoDB repository for product documents
     * @param mongoTemplate     low-level MongoDB template for programmatic queries
     */
    public ProductService(ProductRepository productRepository, MongoTemplate mongoTemplate) {
        this.productRepository = productRepository;
        this.mongoTemplate = mongoTemplate;
    }

    // ── Read operations ───────────────────────────────────────────────────────────

    /**
     * Retrieve all products from the MongoDB collection.
     *
     * <p>Delegates to {@link ProductRepository#findAll()} which issues a MongoDB
     * {@code db.products.find({})} with no filter. For large collections, consider
     * adding pagination via {@code findAll(Pageable)}.
     *
     * @return list of all products (empty list if the collection is empty)
     */
    public List<Product> findAll() {
        return productRepository.findAll();
    }

    /**
     * Retrieve a single product by its MongoDB ObjectId.
     *
     * <p>Returns an {@link Optional} so the caller can handle the "not found"
     * case without null checks. The controller maps an empty Optional to HTTP 404.
     *
     * @param id the MongoDB document ID (hex string)
     * @return an {@link Optional} containing the product, or empty if not found
     */
    public Optional<Product> findById(String id) {
        return productRepository.findById(id);
    }

    /**
     * Retrieve all products in a given category.
     *
     * <p>Delegates to the derived query method
     * {@link ProductRepository#findByCategory(String)}.
     *
     * @param category the category to filter by (case-sensitive)
     * @return list of products in the given category
     */
    public List<Product> findByCategory(String category) {
        return productRepository.findByCategory(category);
    }

    /**
     * Retrieve products whose price is at or below the given maximum.
     *
     * <p>Uses a programmatic {@link MongoTemplate} query with {@link Criteria#lte}
     * rather than a derived repository method. This guarantees correct BSON type
     * handling when comparing a Java {@link BigDecimal} against MongoDB's internal
     * Decimal128 representation across different MongoDB driver versions.
     *
     * <p>Equivalent MongoDB shell query:
     * {@code db.products.find({ price: { $lte: <maxPrice> } })}
     *
     * @param maxPrice maximum price (inclusive)
     * @return list of products priced at or below {@code maxPrice}
     */
    public List<Product> findByMaxPrice(BigDecimal maxPrice) {
        // Criteria.where("price").lte(maxPrice) produces { price: { $lte: <value> } }
        Query query = new Query(Criteria.where("price").lte(maxPrice));
        return mongoTemplate.find(query, Product.class);
    }

    /**
     * Search for products by name (case-insensitive substring match).
     *
     * <p>Delegates to {@link ProductRepository#findByNameContainingIgnoreCase(String)}.
     * Useful for implementing a simple search bar feature.
     *
     * @param name the text to search for within product names
     * @return list of products whose names contain {@code name}
     */
    public List<Product> searchByName(String name) {
        return productRepository.findByNameContainingIgnoreCase(name);
    }

    /**
     * Retrieve products with stock quantity below a given threshold.
     *
     * <p>Delegates to the {@code @Query}-annotated repository method
     * {@link ProductRepository#findLowStockProducts(int)}.
     *
     * @param threshold minimum stock level (exclusive lower bound)
     * @return list of products with stock below {@code threshold}
     */
    public List<Product> findLowStockProducts(int threshold) {
        return productRepository.findLowStockProducts(threshold);
    }

    // ── Write operations ──────────────────────────────────────────────────────────

    /**
     * Create and persist a new product document in MongoDB.
     *
     * <p>Maps the incoming {@link ProductRequest} DTO to a new {@link Product}
     * domain object. The {@code id} field is left null so that MongoDB generates
     * an ObjectId on insert. Both {@code createdAt} and {@code updatedAt} are
     * set to the current instant.
     *
     * <p>{@link ProductRepository#save(Object)} issues a MongoDB {@code insertOne}
     * operation when the {@code id} is null, and an {@code updateOne} with upsert
     * when an {@code id} is present.
     *
     * @param request the product data from the API request body
     * @return the persisted product with its MongoDB-generated {@code id}
     */
    public Product create(ProductRequest request) {
        // Map DTO → domain object; id is null so MongoDB assigns an ObjectId
        Product product = new Product(
                request.getName(),
                request.getDescription(),
                request.getPrice(),
                request.getCategory(),
                request.getStockQuantity()
        );
        // Delegate to the repository — MongoDB assigns the _id on insert
        return productRepository.save(product);
    }

    /**
     * Update an existing product document identified by its ID.
     *
     * <p>This performs a full replacement of all mutable fields (PUT semantics).
     * The {@code id} and {@code createdAt} fields are preserved from the existing
     * document; only the fields provided in the request are overwritten.
     *
     * <p>If no product with the given ID exists, an empty {@link Optional} is
     * returned so the controller can respond with HTTP 404.
     *
     * @param id      the MongoDB document ID of the product to update
     * @param request the new field values
     * @return an {@link Optional} with the updated product, or empty if not found
     */
    public Optional<Product> update(String id, ProductRequest request) {
        // Look up the existing product; return empty if it doesn't exist
        Optional<Product> existing = productRepository.findById(id);
        if (existing.isEmpty()) {
            return Optional.empty();
        }

        // Mutate the existing document in-place to preserve id and createdAt
        Product product = existing.get();
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setCategory(request.getCategory());
        product.setStockQuantity(request.getStockQuantity());
        product.setUpdatedAt(Instant.now()); // reflect the modification time

        // save() with an existing id issues a MongoDB replaceOne (upsert)
        return Optional.of(productRepository.save(product));
    }

    /**
     * Delete a product document by its MongoDB ObjectId.
     *
     * <p>Checks for existence first using {@link ProductRepository#existsById(Object)}
     * to differentiate between "product deleted" (return true) and "product not found"
     * (return false), allowing the controller to respond with 204 vs 404 respectively.
     *
     * @param id the MongoDB document ID of the product to delete
     * @return {@code true} if the product existed and was deleted; {@code false} otherwise
     */
    public boolean deleteById(String id) {
        if (!productRepository.existsById(id)) {
            // Product does not exist — cannot delete something that isn't there
            return false;
        }
        productRepository.deleteById(id);
        return true;
    }
}
