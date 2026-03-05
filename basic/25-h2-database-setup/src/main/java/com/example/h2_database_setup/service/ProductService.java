package com.example.h2_database_setup.service;

import com.example.h2_database_setup.model.Product;
import com.example.h2_database_setup.repository.ProductRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service class for Product business logic.
 */
@Service
public class ProductService {

    private final ProductRepository productRepository;

    public ProductService(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    public List<Product> getAllProducts() {
        return productRepository.findAll();
    }

    public Product saveProduct(Product product) {
        return productRepository.save(product);
    }
}
