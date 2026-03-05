package com.example.h2_database_setup.service;

import com.example.h2_database_setup.model.Product;
import com.example.h2_database_setup.repository.ProductRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ProductServiceTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductService productService;

    @Test
    void testGetAllProducts() {
        Product p1 = new Product("A", 10.0);
        Product p2 = new Product("B", 20.0);
        when(productRepository.findAll()).thenReturn(List.of(p1, p2));

        List<Product> products = productService.getAllProducts();

        assertEquals(2, products.size());
        verify(productRepository, times(1)).findAll();
    }

    @Test
    void testSaveProduct() {
        Product p = new Product("C", 30.0);
        when(productRepository.save(p)).thenReturn(p);

        Product saved = productService.saveProduct(p);

        assertEquals("C", saved.getName());
        verify(productRepository, times(1)).save(p);
    }
}
