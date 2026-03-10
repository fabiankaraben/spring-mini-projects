package com.example.entitylifecycle.repository;

import com.example.entitylifecycle.entity.Product;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Spring Data JPA repository for {@link Product} entities.
 *
 * <p>By extending {@link JpaRepository}, this interface inherits a complete set
 * of CRUD and pagination operations without any implementation code:
 * <ul>
 *   <li>{@code save(entity)}       — INSERT or UPDATE; triggers the JPA lifecycle
 *       callbacks on the entity ({@code @PrePersist}, {@code @PostPersist},
 *       {@code @PreUpdate}) and on the external listener.</li>
 *   <li>{@code findById(id)}       — SELECT by primary key; triggers
 *       {@code @PostLoad} on the loaded entity.</li>
 *   <li>{@code findAll()}          — SELECT all rows; triggers {@code @PostLoad}
 *       on every loaded entity.</li>
 *   <li>{@code deleteById(id)}     — DELETE by primary key; triggers
 *       {@code @PostRemove} on the deleted entity.</li>
 * </ul>
 *
 * <p>Custom query methods below use Spring Data's method-name derivation
 * convention, which translates method names into SQL automatically.
 */
@Repository
public interface ProductRepository extends JpaRepository<Product, Long> {

    /**
     * Finds a product by its unique URL slug.
     *
     * <p>Spring Data translates this method name into:
     * {@code SELECT * FROM products WHERE slug = :slug LIMIT 1}
     *
     * <p>Slugs are set automatically by the {@code @PrePersist} callback and
     * never change afterward ({@code updatable = false}), making them stable
     * permanent identifiers for product URLs.
     *
     * @param slug the URL slug to look up
     * @return an {@link Optional} containing the product, or empty if not found
     */
    Optional<Product> findBySlug(String slug);

    /**
     * Finds all products that have a non-zero discount (i.e. are on sale).
     *
     * <p>Spring Data translates this into:
     * {@code SELECT * FROM products WHERE discount_percent > :minDiscount}
     *
     * @param minDiscount the minimum discount percentage (exclusive); pass 0 to
     *                    find all discounted products
     * @return list of products with a discount greater than {@code minDiscount}
     */
    List<Product> findByDiscountPercentGreaterThan(int minDiscount);
}
