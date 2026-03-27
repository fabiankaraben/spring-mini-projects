package com.example.products.resolver;

import com.example.products.model.Product;
import com.example.products.repository.ProductRepository;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.util.List;

/**
 * GraphQL resolver for the {@code Query} type defined in the products schema.
 *
 * <p>In Spring for GraphQL, resolver classes are annotated with
 * {@link org.springframework.stereotype.Controller} (not @Service or @Component).
 * This lets Spring MVC recognize the class and register the annotated methods
 * as GraphQL operation handlers.
 *
 * <p>Each {@link QueryMapping} method corresponds to a field on the GraphQL
 * {@code Query} type in {@code schema.graphqls}. The method name must exactly
 * match the field name declared in the schema, or a {@code typeName} / {@code field}
 * attribute must be provided.
 *
 * <p>Method arguments annotated with {@link Argument} are automatically mapped
 * from the incoming GraphQL operation's arguments (similar to @RequestParam in
 * Spring MVC, but for GraphQL).
 */
@Controller
public class ProductResolver {

    private final ProductRepository productRepository;

    /**
     * Constructor injection is preferred over field injection because it makes
     * dependencies explicit and eases unit testing without a Spring context.
     */
    public ProductResolver(ProductRepository productRepository) {
        this.productRepository = productRepository;
    }

    /**
     * Resolves the {@code products} query field.
     *
     * <p>Returns all products in the catalogue. Clients can further filter the
     * result on the client side, or use the more specific {@code productsByCategory}
     * or {@code productsInStock} queries.
     *
     * <p>GraphQL query:
     * <pre>
     * query {
     *   products {
     *     id
     *     name
     *     price
     *   }
     * }
     * </pre>
     *
     * @return list of all products (never null; returns empty list when empty)
     */
    @QueryMapping
    public List<Product> products() {
        return productRepository.findAll();
    }

    /**
     * Resolves the {@code product(id: ID!)} query field.
     *
     * <p>Returns a single product by its unique ID, or {@code null} if no
     * product with the given ID exists. GraphQL translates {@code null} to a
     * JSON {@code null} value (assuming the schema field is nullable, i.e.,
     * {@code product(id: ID!): Product}).
     *
     * <p>GraphQL query:
     * <pre>
     * query {
     *   product(id: "prod-1") {
     *     id
     *     name
     *     price
     *     inStock
     *   }
     * }
     * </pre>
     *
     * @param id the product ID to look up (never null — declared ID! in schema)
     * @return the found product, or {@code null} if not found
     */
    @QueryMapping
    public Product product(@Argument String id) {
        return productRepository.findById(id).orElse(null);
    }

    /**
     * Resolves the {@code productsByCategory(category: String!)} query field.
     *
     * <p>Returns all products belonging to the specified category. The comparison
     * is case-insensitive (see {@link ProductRepository#findByCategory(String)}).
     *
     * <p>GraphQL query:
     * <pre>
     * query {
     *   productsByCategory(category: "Electronics") {
     *     id
     *     name
     *     price
     *   }
     * }
     * </pre>
     *
     * @param category category name to filter by
     * @return list of matching products
     */
    @QueryMapping
    public List<Product> productsByCategory(@Argument String category) {
        return productRepository.findByCategory(category);
    }

    /**
     * Resolves the {@code productsInStock} query field.
     *
     * <p>Returns all products that are currently in stock.
     *
     * <p>GraphQL query:
     * <pre>
     * query {
     *   productsInStock {
     *     id
     *     name
     *     inStock
     *   }
     * }
     * </pre>
     *
     * @return list of products with {@code inStock == true}
     */
    @QueryMapping
    public List<Product> productsInStock() {
        return productRepository.findInStock();
    }
}
