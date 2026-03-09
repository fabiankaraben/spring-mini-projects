package com.example.mongodbcrudapi.repository;

import com.example.mongodbcrudapi.domain.Product;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

/**
 * Spring Data MongoDB repository for the {@link Product} document.
 *
 * <p>By extending {@link MongoRepository}, this interface gets a full suite of
 * CRUD operations for free — no implementation class is needed. Spring Data
 * generates a dynamic proxy at startup that provides implementations for all
 * inherited methods and the custom query methods declared below.
 *
 * <p>Generic type parameters:
 * <ul>
 *   <li>{@code Product} – the document type managed by this repository.</li>
 *   <li>{@code String}  – the type of the document's {@code @Id} field. MongoDB
 *       ObjectIds are represented as hex strings when using Spring Data MongoDB.</li>
 * </ul>
 *
 * <p>Inherited methods (from {@link MongoRepository} → {@code CrudRepository}):
 * <ul>
 *   <li>{@code save(Product)} – insert or update a document.</li>
 *   <li>{@code findById(String)} – find a document by its {@code _id}.</li>
 *   <li>{@code findAll()} – return all documents in the collection.</li>
 *   <li>{@code deleteById(String)} – remove a document by its {@code _id}.</li>
 *   <li>{@code count()} – total number of documents.</li>
 *   <li>{@code existsById(String)} – check existence without loading the document.</li>
 * </ul>
 *
 * <p>Custom derived query methods follow Spring Data's naming convention:
 * {@code findBy<FieldName>} is parsed at startup and translated into the
 * appropriate MongoDB query. No boilerplate query code is needed.
 */
@Repository
public interface ProductRepository extends MongoRepository<Product, String> {

    /**
     * Find all products that belong to a given category.
     *
     * <p>Spring Data translates this method name into the MongoDB query:
     * {@code { "category": "<categoryValue>" }}
     * This is called a <em>derived query</em> — Spring Data reads the method name
     * and builds the query automatically.
     *
     * @param category the category to filter by (case-sensitive)
     * @return list of products in the given category (empty if none found)
     */
    List<Product> findByCategory(String category);

    /**
     * Find all products whose price is less than or equal to the given maximum.
     *
     * <p>Uses an explicit {@link Query} with a MongoDB {@code $lte} operator instead
     * of the derived method name. This avoids potential BSON type-conversion
     * inconsistencies between Java {@link BigDecimal} and MongoDB's Decimal128 type
     * that can occur with derived query methods in some driver versions.
     *
     * <p>The {@code ?0} placeholder is replaced with the first method parameter
     * ({@code maxPrice}) at runtime.
     *
     * @param maxPrice maximum price (inclusive)
     * @return list of products priced at or below {@code maxPrice}
     */
    @Query("{ 'price': { $lte: ?0 } }")
    List<Product> findByPriceLessThanEqual(BigDecimal maxPrice);

    /**
     * Find all products whose name contains the given text, case-insensitively.
     *
     * <p>Spring Data translates {@code ContainingIgnoreCase} into a MongoDB
     * regex query: {@code { "name": { "$regex": "<text>", "$options": "i" } }}
     *
     * <p>Note: regex queries cannot use a standard index. For production
     * full-text search, prefer MongoDB's {@code $text} operator with a text index.
     *
     * @param text the substring to search for in product names
     * @return list of products whose names contain {@code text} (case-insensitive)
     */
    List<Product> findByNameContainingIgnoreCase(String text);

    /**
     * Find products in a specific category with a price below the given maximum.
     *
     * <p>This demonstrates a <em>compound derived query</em> — Spring Data
     * combines two conditions with a logical AND:
     * {@code { "category": "<category>", "price": { "$lte": <maxPrice> } }}
     *
     * @param category the product category
     * @param maxPrice the maximum price (inclusive)
     * @return list of matching products
     */
    List<Product> findByCategoryAndPriceLessThanEqual(String category, BigDecimal maxPrice);

    /**
     * Count the number of products in a given category.
     *
     * <p>Derived count query – Spring Data generates:
     * {@code db.products.countDocuments({ "category": "<category>" })}
     *
     * @param category the category to count products in
     * @return number of products in the category
     */
    long countByCategory(String category);

    /**
     * Find products with stock quantity below the given threshold.
     *
     * <p>This uses the {@link Query} annotation with a MongoDB JSON query string
     * to demonstrate explicit query definition as an alternative to derived queries.
     * The {@code ?0} placeholder is replaced with the first method parameter at runtime.
     *
     * <p>Equivalent derived method name would be {@code findByStockQuantityLessThan}.
     *
     * @param threshold minimum stock level (exclusive)
     * @return products whose stock quantity is strictly less than {@code threshold}
     */
    @Query("{ 'stock_quantity': { $lt: ?0 } }")
    List<Product> findLowStockProducts(int threshold);
}
