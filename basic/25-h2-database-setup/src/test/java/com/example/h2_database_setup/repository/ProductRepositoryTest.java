package com.example.h2_database_setup.repository;

import com.example.h2_database_setup.model.Product;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest
class ProductRepositoryTest {

    @Autowired
    private ProductRepository productRepository;

    @Test
    void testSaveAndFindByName() {
        Product product = new Product("Keyboard", 50.0);
        productRepository.save(product);

        List<Product> products = productRepository.findByNameContainingIgnoreCase("key");

        assertEquals(1, products.size());
        assertEquals("Keyboard", products.get(0).getName());
    }
}
