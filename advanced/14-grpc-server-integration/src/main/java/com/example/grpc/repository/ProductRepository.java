package com.example.grpc.repository;

import com.example.grpc.domain.Product;
import com.example.grpc.domain.ProductStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Spring Data JPA repository for {@link Product} entities.
 *
 * <p>Spring Data automatically generates an implementation at startup by scanning
 * for interfaces that extend {@link JpaRepository}. No boilerplate SQL or
 * EntityManager calls are needed for standard CRUD and query-by-example operations.
 *
 * <p>Query method naming convention:
 *   {@code findBy<Field>(<value>)} generates a {@code SELECT * FROM products WHERE field = value}.
 *   {@code findBy<Field>OrderBy<Other>Asc} appends {@code ORDER BY other ASC}.
 */
@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    /**
     * Find all products that belong to the given category, ordered by name ascending.
     *
     * <p>Spring Data translates this method name into:
     * <pre>SELECT p FROM Product p WHERE p.category = :category ORDER BY p.name ASC</pre>
     *
     * @param category the product category to filter by (case-sensitive)
     * @return list of matching products, may be empty
     */
    List<Product> findByCategoryOrderByNameAsc(String category);

    /**
     * Find all products that do NOT have the given status, ordered by name ascending.
     *
     * <p>Used by the ListProducts RPC to exclude DISCONTINUED (soft-deleted) products
     * from the default listing when no category filter is applied.
     *
     * <p>Spring Data translates this into:
     * <pre>SELECT p FROM Product p WHERE p.status != :status ORDER BY p.name ASC</pre>
     *
     * @param status the status to exclude
     * @return list of products with any other status, may be empty
     */
    List<Product> findByStatusNotOrderByNameAsc(ProductStatus status);

    /**
     * Find all products in the given category that do NOT have the given status.
     *
     * <p>Used by the ListProducts RPC when both a category filter and the
     * DISCONTINUED exclusion are needed simultaneously.
     *
     * <p>Spring Data translates this into:
     * <pre>SELECT p FROM Product p
     * WHERE p.category = :category AND p.status != :status
     * ORDER BY p.name ASC</pre>
     *
     * @param category the product category to filter by
     * @param status   the status to exclude
     * @return list of matching products, may be empty
     */
    List<Product> findByCategoryAndStatusNotOrderByNameAsc(String category, ProductStatus status);
}
