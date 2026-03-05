package com.example.h2_database_setup.controller;

import com.example.h2_database_setup.model.Product;
import com.example.h2_database_setup.service.ProductService;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller exposing Product endpoints.
 */
@RestController
@RequestMapping("/api/products")
public class ProductController {

    private final ProductService productService;

    public ProductController(ProductService productService) {
        this.productService = productService;
    }

    @GetMapping
    public List<Product> getAllProducts() {
        return productService.getAllProducts();
    }

    @PostMapping
    public Product createProduct(@RequestBody Product product) {
        return productService.saveProduct(product);
    }
}
