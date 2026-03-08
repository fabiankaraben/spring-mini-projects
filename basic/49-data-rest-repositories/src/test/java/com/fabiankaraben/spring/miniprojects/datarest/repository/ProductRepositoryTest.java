package com.fabiankaraben.spring.miniprojects.datarest.repository;

import com.fabiankaraben.spring.miniprojects.datarest.model.Product;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Sliced integration test for the ProductRepository using @DataJpaTest.
 */
@DataJpaTest
class ProductRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ProductRepository productRepository;

    @Test
    void whenFindByName_thenReturnProduct() {
        // given
        Product product = new Product("Laptop", "High-end laptop", 1500.0);
        entityManager.persist(product);
        entityManager.flush();

        // when
        List<Product> found = productRepository.findByName("Laptop");

        // then
        assertThat(found).hasSize(1);
        assertThat(found.get(0).getName()).isEqualTo(product.getName());
    }

    @Test
    void whenFindByName_thenReturnEmptyList_ifNotFound() {
        // when
        List<Product> found = productRepository.findByName("NonExistent");

        // then
        assertThat(found).isEmpty();
    }
}
