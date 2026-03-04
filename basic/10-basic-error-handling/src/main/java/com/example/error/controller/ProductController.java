package com.example.error.controller;

import com.example.error.model.Product;
import com.example.error.service.ProductService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

/**
 * Controller demonstrating various error scenarios by delegating logic to the
 * service.
 */
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    /**
     * Retrieves a product by ID.
     * The service throws ProductNotFoundException if id is not 1.
     *
     * @param id the product ID
     * @return the Product
     */
    @GetMapping("/{id}")
    public Product getProduct(@PathVariable Long id) {
        return productService.getProduct(id);
    }

    /**
     * Creates a new product.
     * The service throws InvalidProductException if the price is negative.
     *
     * @param product the product to create
     * @return the created product
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Product createProduct(@RequestBody Product product) {
        return productService.createProduct(product);
    }
}
