package com.example.resourceserver.service;

import com.example.resourceserver.domain.Product;
import com.example.resourceserver.dto.CreateProductRequest;
import com.example.resourceserver.dto.UpdateProductRequest;
import com.example.resourceserver.repository.ProductRepository;

import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Service layer for the Products domain.
 *
 * <p>This class implements the business logic for managing products. It sits between
 * the REST controller (HTTP layer) and the repository (data layer), following the
 * classic three-tier architecture:
 * <pre>
 *   Controller → Service → Repository
 * </pre>
 *
 * <p><b>Responsibilities of this service:</b>
 * <ul>
 *   <li>Map incoming DTOs ({@link CreateProductRequest}, {@link UpdateProductRequest})
 *       to domain objects ({@link Product}).</li>
 *   <li>Apply business rules (e.g., validation beyond field-level checks).</li>
 *   <li>Delegate persistence operations to the {@link ProductRepository}.</li>
 * </ul>
 *
 * <p><b>Why separate service and repository?</b>
 * <ul>
 *   <li>The service is unit-testable in isolation by mocking the repository.</li>
 *   <li>Business logic lives in one place (the service), not scattered across controllers.</li>
 *   <li>The controller remains thin — it only handles HTTP concerns (request/response mapping).</li>
 * </ul>
 */
@Service
public class ProductService {

    /**
     * The data access layer. Injected via constructor injection — the Spring-recommended
     * approach because it makes dependencies explicit and supports testing without Spring.
     */
    private final ProductRepository productRepository;

    /**
     * Constructs the service with its required repository dependency.
     *
     * @param productRepository the in-memory product data store
     */
    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    /**
     * Returns all products in the catalog.
     *
     * <p>The result is a snapshot list — changes made after this call are not reflected.
     *
     * @return a list of all products; never null, may be empty
     */
    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    /**
     * Finds a product by its unique ID.
     *
     * @param id the product ID to look up
     * @return an {@link Optional} containing the product if found, or empty if not found
     */
    public Optional<Product> getProductById(Long id) {
        return productRepository.findById(id);
    }

    /**
     * Returns all products in the specified category.
     *
     * <p>The category comparison is case-insensitive (delegated to the repository).
     *
     * @param category the category to filter by (e.g., "ELECTRONICS")
     * @return a list of matching products; may be empty
     */
    public List<Product> getProductsByCategory(String category) {
        return productRepository.findByCategory(category);
    }

    /**
     * Creates a new product from the provided request DTO.
     *
     * <p>The business rules applied here:
     * <ul>
     *   <li>The request DTO is mapped to a new {@link Product} domain object.</li>
     *   <li>The repository assigns the ID and timestamps.</li>
     * </ul>
     *
     * @param request the create request DTO with validated field values
     * @return the persisted product with its assigned ID and timestamps
     */
    public Product createProduct(CreateProductRequest request) {
        // Map the request DTO to a domain object.
        // This decouples the API contract (DTO) from the domain model (Product).
        Product product = new Product();
        product.setName(request.getName());
        product.setDescription(request.getDescription());
        product.setPrice(request.getPrice());
        product.setCategory(request.getCategory());
        product.setStock(request.getStock());

        // Delegate persistence to the repository (ID and timestamps are assigned there)
        return productRepository.save(product);
    }

    /**
     * Updates an existing product with the values from the request DTO.
     *
     * <p>Only non-null fields in the request are applied to the existing product.
     * This implements partial-update semantics: a client can update just the price
     * without needing to resend all other fields.
     *
     * @param id      the ID of the product to update
     * @param request the update request DTO (only non-null fields are applied)
     * @return an {@link Optional} containing the updated product, or empty if not found
     */
    public Optional<Product> updateProduct(Long id, UpdateProductRequest request) {
        // Look up the existing product — return empty if not found
        return productRepository.findById(id).map(existing -> {

            // Apply each non-null field from the request (partial update)
            if (request.getName() != null) {
                existing.setName(request.getName());
            }
            if (request.getDescription() != null) {
                existing.setDescription(request.getDescription());
            }
            if (request.getPrice() != null) {
                existing.setPrice(request.getPrice());
            }
            if (request.getCategory() != null) {
                existing.setCategory(request.getCategory());
            }
            if (request.getStock() != null) {
                existing.setStock(request.getStock());
            }

            // Persist the updated product (repository sets updatedAt)
            return productRepository.update(existing);
        });
    }

    /**
     * Deletes the product with the given ID.
     *
     * @param id the ID of the product to delete
     * @return {@code true} if the product was deleted, {@code false} if no product had that ID
     */
    public boolean deleteProduct(Long id) {
        return productRepository.deleteById(id);
    }

    /**
     * Returns the total count of products in the catalog.
     *
     * @return the total number of products
     */
    public int getProductCount() {
        return productRepository.count();
    }
}
