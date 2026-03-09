package com.example.mongodbcustomqueries.repository;

import com.example.mongodbcustomqueries.domain.Order;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

/**
 * Spring Data MongoDB repository for the {@link Order} document.
 *
 * <p>By extending {@link MongoRepository}, this interface gets a full suite of
 * CRUD operations for free — no implementation class is needed. Spring Data
 * generates a dynamic proxy at startup that provides implementations for all
 * inherited methods and the custom query methods declared below.
 *
 * <p>Generic type parameters:
 * <ul>
 *   <li>{@code Order}  – the document type managed by this repository.</li>
 *   <li>{@code String} – the type of the document's {@code @Id} field.</li>
 * </ul>
 *
 * <p>This repository intentionally keeps only simple queries. Complex aggregation
 * pipelines (grouping, unwinding, multi-stage projections) are implemented in
 * {@link com.example.mongodbcustomqueries.service.OrderAggregationService} using
 * {@code MongoTemplate} for full programmatic control over the pipeline.
 */
@Repository
public interface OrderRepository extends MongoRepository<Order, String> {

    /**
     * Find all orders placed by a specific customer.
     *
     * <p>Spring Data translates this method name into:
     * {@code { "customer_name": "<customerName>" }}
     *
     * @param customerName the customer identifier to filter by
     * @return list of orders for the given customer (empty if none found)
     */
    List<Order> findByCustomerName(String customerName);

    /**
     * Find all orders in a specific geographic region.
     *
     * <p>Spring Data derived query translates to:
     * {@code { "region": "<region>" }}
     *
     * @param region the region to filter by
     * @return list of orders in the given region
     */
    List<Order> findByRegion(String region);

    /**
     * Find all orders with a given status.
     *
     * <p>Spring Data derived query translates to:
     * {@code { "status": "<status>" }}
     *
     * @param status the order status to filter by (e.g. "DELIVERED", "PENDING")
     * @return list of orders with the given status
     */
    List<Order> findByStatus(String status);

    /**
     * Find all orders with a total amount greater than or equal to the given minimum.
     *
     * <p>Uses an explicit {@link Query} annotation to demonstrate JSON query
     * syntax as an alternative to derived method names. The {@code ?0} placeholder
     * is replaced with the first method parameter at runtime.
     *
     * @param minAmount minimum total amount (inclusive)
     * @return list of orders with total amount at or above {@code minAmount}
     */
    @Query("{ 'total_amount': { $gte: ?0 } }")
    List<Order> findByTotalAmountGreaterThanEqual(BigDecimal minAmount);

    /**
     * Find all orders placed within a given time range.
     *
     * <p>Uses a {@link Query} annotation with two placeholders:
     * {@code ?0} = start instant (inclusive), {@code ?1} = end instant (inclusive).
     *
     * @param start the start of the time range
     * @param end   the end of the time range
     * @return list of orders created between {@code start} and {@code end}
     */
    @Query("{ 'created_at': { $gte: ?0, $lte: ?1 } }")
    List<Order> findOrdersInTimeRange(Instant start, Instant end);

    /**
     * Count the number of orders for a given status.
     *
     * <p>Derived count query — Spring Data generates:
     * {@code db.orders.countDocuments({ "status": "<status>" })}
     *
     * @param status the order status
     * @return count of orders with the given status
     */
    long countByStatus(String status);
}
