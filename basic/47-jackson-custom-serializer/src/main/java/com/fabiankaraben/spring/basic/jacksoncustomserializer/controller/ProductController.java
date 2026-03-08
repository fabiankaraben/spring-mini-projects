package com.fabiankaraben.spring.basic.jacksoncustomserializer.controller;

import com.fabiankaraben.spring.basic.jacksoncustomserializer.model.Product;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/products")
public class ProductController {

    @PostMapping
    public Product createProduct(@RequestBody Product product) {
        // In a real app, this would save to DB
        // Here we just return it to verify deserialization -> serialization loop
        return product;
    }
    
    @GetMapping("/example")
    public Product getExample() {
        return new Product("Premium Headphones", 29999, "Electronics");
    }
}
