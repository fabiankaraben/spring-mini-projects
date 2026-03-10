package com.example.softdelete.exception;

/**
 * Thrown when a product with a given ID cannot be found among the active (non-deleted) records.
 *
 * <p>Because of the {@code @SQLRestriction("deleted = false")} annotation on the
 * {@link com.example.softdelete.domain.Product} entity, a soft-deleted product is
 * indistinguishable from a truly non-existent product from the service layer's perspective.
 * Both cases produce this exception, and the REST controller maps it to a 404 response.</p>
 */
public class ProductNotFoundException extends RuntimeException {

    /**
     * Constructs a new exception with a message indicating which product ID was not found.
     *
     * @param id the product ID that could not be found
     */
    public ProductNotFoundException(Long id) {
        super("Product not found with id: " + id);
    }
}
