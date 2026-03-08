package com.fabiankaraben.spring.miniprojects.datarest.repository;

import com.fabiankaraben.spring.miniprojects.datarest.model.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;
import org.springframework.data.rest.core.annotation.RestResource;

import java.util.List;

/**
 * Repository interface for Product entity.
 * 
 * By extending JpaRepository and having spring-boot-starter-data-rest on the classpath,
 * Spring Data REST automatically exposes this repository as REST endpoints.
 * 
 * @RepositoryRestResource is optional but allows customizing the path and collection resource rel.
 * Here we map it to /products (default would be products anyway).
 */
@RepositoryRestResource(collectionResourceRel = "products", path = "products")
public interface ProductRepository extends JpaRepository<Product, Long> {

    /**
     * Custom query method to find products by name.
     * Spring Data REST exposes this as a search resource: /products/search/findByName?name={name}
     * 
     * @param name the name of the product
     * @return a list of products with the given name
     */
    @RestResource(path = "by-name", rel = "by-name")
    List<Product> findByName(String name);
}
