package com.example.error.service;

import com.example.error.exception.InvalidProductException;
import com.example.error.exception.ProductNotFoundException;
import com.example.error.model.Product;
import org.springframework.stereotype.Service;

/**
 * Service handling business logic for products.
 */
@Service
public class ProductService {

    /**
     * Retrieves a product by ID.
     * Throws ProductNotFoundException if the ID is not equal to 1.
     *
     * @param id the product ID
     * @return the requested Product
     */
    public Product getProduct(Long id) {
        if (id != 1L) {
            // Throwing custom exception when business entity is not found
            throw new ProductNotFoundException(id);
        }
        return new Product(1L, "Sample Product", 99.99);
    }

    /**
     * Creates a new product.
     * Throws InvalidProductException if the price is negative.
     *
     * @param product the product details to create
     * @return the created Product
     */
    public Product createProduct(Product product) {
        if (product.price() < 0) {
            // Throwing custom validation exception
            throw new InvalidProductException("Price cannot be negative");
        }
        Long newId = (product.id() == null) ? 2L : product.id();
        return new Product(newId, product.name(), product.price());
    }
}
