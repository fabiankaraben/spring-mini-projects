package com.example.mongodbcustomqueries.service;

import com.example.mongodbcustomqueries.domain.Order;
import com.example.mongodbcustomqueries.dto.CustomerSummaryResult;
import com.example.mongodbcustomqueries.dto.OrderRequest;
import com.example.mongodbcustomqueries.dto.RevenueByRegionResult;
import com.example.mongodbcustomqueries.dto.TopProductResult;
import com.example.mongodbcustomqueries.repository.OrderRepository;
import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.aggregation.ArithmeticOperators;
import org.springframework.data.mongodb.core.aggregation.ConditionalOperators;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Service layer containing both CRUD operations and complex MongoDB aggregation queries.
 *
 * <p>This class is the educational core of the mini-project. It demonstrates how to
 * build MongoDB Aggregation Pipelines using Spring Data's
 * {@link org.springframework.data.mongodb.core.aggregation.Aggregation} fluent API,
 * which is backed by {@link MongoTemplate}.
 *
 * <h2>Why MongoTemplate instead of MongoRepository?</h2>
 * <p>{@link org.springframework.data.mongodb.repository.MongoRepository} derived methods
 * and {@code @Query} annotations cover simple find/filter operations, but they cannot
 * express multi-stage aggregation pipelines. {@link MongoTemplate} provides the full
 * power of MongoDB's aggregation framework:
 * <ul>
 *   <li>{@code $match}   – filter documents before or after grouping.</li>
 *   <li>{@code $group}   – group documents and compute accumulators (sum, avg, max, min, count).</li>
 *   <li>{@code $unwind}  – deconstruct an array field into separate documents.</li>
 *   <li>{@code $project} – reshape documents: include, exclude, or compute fields.</li>
 *   <li>{@code $sort}    – order results by one or more fields.</li>
 *   <li>{@code $limit}   – restrict the number of output documents.</li>
 * </ul>
 *
 * <h2>Pipeline execution model</h2>
 * <p>Each stage receives the output of the previous stage. MongoDB processes the
 * stages in order, and only the final stage's output is returned to the application.
 * This means filtering early (with {@code $match}) before grouping ({@code $group})
 * is much more efficient than filtering after, because less data flows through
 * the pipeline.
 */
@Service
public class OrderAggregationService {

    private final OrderRepository orderRepository;

    /**
     * {@link MongoTemplate} provides the low-level aggregation API.
     * It is injected here to build and execute custom aggregation pipelines
     * that cannot be expressed with derived repository methods.
     */
    private final MongoTemplate mongoTemplate;

    /**
     * Constructor injection makes dependencies explicit and testable.
     * Mockito can inject mocks without needing the Spring context.
     *
     * @param orderRepository the MongoDB repository for basic CRUD operations
     * @param mongoTemplate   the template used to execute aggregation pipelines
     */
    public OrderAggregationService(OrderRepository orderRepository, MongoTemplate mongoTemplate) {
        this.orderRepository = orderRepository;
        this.mongoTemplate = mongoTemplate;
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // CRUD Operations
    // ══════════════════════════════════════════════════════════════════════════════

    /**
     * Create and persist a new order document in MongoDB.
     *
     * <p>Maps the incoming {@link OrderRequest} DTO to a new {@link Order} domain
     * object. The {@code id} is left null so MongoDB generates an ObjectId on insert.
     *
     * @param request the order data from the API request body
     * @return the persisted order with its MongoDB-generated {@code id}
     */
    public Order create(OrderRequest request) {
        // Map DTO → domain object; id is null so MongoDB generates the ObjectId
        Order order = new Order(
                request.getCustomerName(),
                request.getRegion(),
                request.getStatus(),
                request.getTotalAmount(),
                request.getItems()
        );
        return orderRepository.save(order);
    }

    /**
     * Retrieve all orders from the MongoDB collection.
     *
     * @return list of all orders (empty list if the collection is empty)
     */
    public List<Order> findAll() {
        return orderRepository.findAll();
    }

    /**
     * Retrieve a single order by its MongoDB ObjectId.
     *
     * @param id the MongoDB document ID (hex string)
     * @return an {@link Optional} containing the order, or empty if not found
     */
    public Optional<Order> findById(String id) {
        return orderRepository.findById(id);
    }

    /**
     * Delete an order document by its MongoDB ObjectId.
     *
     * @param id the MongoDB document ID of the order to delete
     * @return {@code true} if the order existed and was deleted; {@code false} otherwise
     */
    public boolean deleteById(String id) {
        if (!orderRepository.existsById(id)) {
            return false;
        }
        orderRepository.deleteById(id);
        return true;
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Aggregation Query 1: Revenue by Region
    // ══════════════════════════════════════════════════════════════════════════════

    /**
     * Compute total revenue, order count, and average order value for each region.
     *
     * <h3>Aggregation Pipeline</h3>
     * <ol>
     *   <li>{@code $group} – group all orders by {@code region} field, computing:
     *       <ul>
     *         <li>{@code totalRevenue} = {@code $sum} of {@code total_amount}</li>
     *         <li>{@code orderCount}   = {@code $sum: 1} (count documents in each group)</li>
     *         <li>{@code avgOrderValue} = {@code $avg} of {@code total_amount}</li>
     *       </ul>
     *   </li>
     *   <li>{@code $sort} – order by {@code totalRevenue} descending (highest revenue first).</li>
     * </ol>
     *
     * <h3>Equivalent MongoDB shell query</h3>
     * <pre>
     * db.orders.aggregate([
     *   { $group: {
     *       _id: "$region",
     *       totalRevenue:   { $sum: "$total_amount" },
     *       orderCount:     { $sum: 1 },
     *       avgOrderValue:  { $avg: "$total_amount" }
     *   }},
     *   { $sort: { totalRevenue: -1 } }
     * ])
     * </pre>
     *
     * @return list of per-region revenue statistics, sorted by revenue descending
     */
    public List<RevenueByRegionResult> getRevenueByRegion() {
        // Stage 1: $group by region, computing revenue statistics.
        //
        // IMPORTANT: Aggregation.group() and the accumulator methods (.sum(), .avg(), etc.)
        // do NOT translate Java property names to @Field-annotated MongoDB field names.
        // You MUST use the actual MongoDB document field name (snake_case as stored in BSON).
        //   ✓ group("region")       → _id: "$region"        (same in Java and MongoDB)
        //   ✓ sum("total_amount")   → $sum: "$total_amount"  (MongoDB field name)
        //   ✗ sum("totalAmount")    → $sum: "$totalAmount"   (field doesn't exist → 0)
        var groupStage = Aggregation.group("region")
                // $sum of total_amount gives total revenue per region
                .sum("total_amount").as("totalRevenue")
                // $sum of 1 counts how many orders are in this group
                .count().as("orderCount")
                // $avg of total_amount gives the average order value
                .avg("total_amount").as("avgOrderValue");

        // Stage 2: $project to rename _id back to "region" for the result DTO.
        // The group stage sets _id to the region value; we expose it as "region".
        // Accumulated fields (totalRevenue, orderCount, avgOrderValue) must be explicitly
        // included in the project so they flow through to the output document.
        var projectStage = Aggregation.project("totalRevenue", "orderCount", "avgOrderValue")
                .and("_id").as("region");

        // Stage 3: $sort by totalRevenue descending.
        // Sort AFTER project so the "totalRevenue" alias is already available.
        var sortStage = Aggregation.sort(Sort.by(Sort.Direction.DESC, "totalRevenue"));

        // Assemble the pipeline: group → project → sort
        Aggregation aggregation = Aggregation.newAggregation(groupStage, projectStage, sortStage);

        // Execute the pipeline against the "orders" collection and map to result DTO
        AggregationResults<RevenueByRegionResult> results =
                mongoTemplate.aggregate(aggregation, "orders", RevenueByRegionResult.class);

        return results.getMappedResults();
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Aggregation Query 2: Top Products by Revenue (with $unwind)
    // ══════════════════════════════════════════════════════════════════════════════

    /**
     * Find the top-N products ranked by total revenue generated across all orders.
     *
     * <h3>Key Concept: {@code $unwind}</h3>
     * <p>Each order document contains an {@code items} array. To compute per-product
     * statistics, we must first "explode" that array so that each item becomes its
     * own document. The {@code $unwind} stage does this. After unwinding, a single
     * order with 3 items becomes 3 documents, each with one item.
     *
     * <h3>Aggregation Pipeline</h3>
     * <ol>
     *   <li>{@code $unwind} – deconstruct the {@code items} array into one doc per item.</li>
     *   <li>{@code $group}  – group by {@code items.product_name}, computing:
     *       <ul>
     *         <li>{@code totalRevenue} = {@code $sum: { $multiply: [unit_price, quantity] }}</li>
     *         <li>{@code totalQuantity} = {@code $sum: "$items.quantity"}</li>
     *       </ul>
     *   </li>
     *   <li>{@code $sort}  – order by {@code totalRevenue} descending.</li>
     *   <li>{@code $limit} – keep only the top-N results.</li>
     * </ol>
     *
     * <h3>Equivalent MongoDB shell query</h3>
     * <pre>
     * db.orders.aggregate([
     *   { $unwind: "$items" },
     *   { $group: {
     *       _id: "$items.product_name",
     *       totalRevenue:  { $sum: { $multiply: ["$items.unit_price", "$items.quantity"] } },
     *       totalQuantity: { $sum: "$items.quantity" }
     *   }},
     *   { $sort:  { totalRevenue: -1 } },
     *   { $limit: topN }
     * ])
     * </pre>
     *
     * @param topN the number of top products to return
     * @return list of top products ordered by total revenue descending
     */
    public List<TopProductResult> getTopProductsByRevenue(int topN) {
        // Stage 1: $unwind the items array — each item in the array becomes its own doc
        var unwindStage = Aggregation.unwind("items");

        // Stage 2: $group by product name, computing revenue and quantity.
        // Use the actual MongoDB field names stored in BSON (snake_case), NOT Java property names.
        // "items.product_name" is the MongoDB path after $unwind (mapped from @Field("product_name")).
        // "items.unit_price" and "items.quantity" are the MongoDB field names inside each item.
        var groupStage = Aggregation.group("items.product_name")
                // $sum: { $multiply: [unit_price, quantity] } — item line total revenue
                .sum(ArithmeticOperators.Multiply.valueOf("items.unit_price")
                        .multiplyBy("items.quantity")).as("totalRevenue")
                // $sum: quantity — total units sold for this product
                .sum("items.quantity").as("totalQuantity");

        // Stage 3: $project to rename _id → productName for the result DTO.
        // Must come before $sort so the "totalRevenue" alias is available to sort on.
        var projectStage = Aggregation.project("totalRevenue", "totalQuantity")
                .and("_id").as("productName");

        // Stage 4: $sort by totalRevenue descending (after project so alias is visible)
        var sortStage = Aggregation.sort(Sort.by(Sort.Direction.DESC, "totalRevenue"));

        // Stage 5: $limit to keep only the top-N results
        var limitStage = Aggregation.limit(topN);

        // Assemble the pipeline: unwind → group → project → sort → limit
        Aggregation aggregation = Aggregation.newAggregation(
                unwindStage, groupStage, projectStage, sortStage, limitStage);

        AggregationResults<TopProductResult> results =
                mongoTemplate.aggregate(aggregation, "orders", TopProductResult.class);

        return results.getMappedResults();
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Aggregation Query 3: Customer Spending Summary (with $match filter)
    // ══════════════════════════════════════════════════════════════════════════════

    /**
     * Summarise spending statistics per customer, excluding cancelled orders.
     *
     * <h3>Key Concept: {@code $match} before {@code $group}</h3>
     * <p>Placing a {@code $match} stage at the beginning of the pipeline filters
     * documents <em>before</em> the expensive {@code $group} stage. This is the
     * most important optimisation in aggregation pipelines: MongoDB can use indexes
     * on the match fields and reduce the volume of data that flows into subsequent stages.
     *
     * <h3>Aggregation Pipeline</h3>
     * <ol>
     *   <li>{@code $match} – exclude cancelled orders ({@code status != "CANCELLED"}).</li>
     *   <li>{@code $group} – group by customer name, computing:
     *       <ul>
     *         <li>{@code totalSpent}  = {@code $sum: "$total_amount"}</li>
     *         <li>{@code orderCount}  = {@code $sum: 1}</li>
     *         <li>{@code maxOrder}    = {@code $max: "$total_amount"}</li>
     *         <li>{@code minOrder}    = {@code $min: "$total_amount"}</li>
     *       </ul>
     *   </li>
     *   <li>{@code $sort} – order by {@code totalSpent} descending (highest spenders first).</li>
     * </ol>
     *
     * <h3>Equivalent MongoDB shell query</h3>
     * <pre>
     * db.orders.aggregate([
     *   { $match: { status: { $ne: "CANCELLED" } } },
     *   { $group: {
     *       _id: "$customer_name",
     *       totalSpent: { $sum: "$total_amount" },
     *       orderCount: { $sum: 1 },
     *       maxOrder:   { $max: "$total_amount" },
     *       minOrder:   { $min: "$total_amount" }
     *   }},
     *   { $sort: { totalSpent: -1 } }
     * ])
     * </pre>
     *
     * @return list of customer spending summaries, sorted by total spent descending
     */
    public List<CustomerSummaryResult> getCustomerSpendingSummary() {
        // Stage 1: $match – filter out cancelled orders before grouping
        // Criteria.where("status").ne("CANCELLED") → { status: { $ne: "CANCELLED" } }
        var matchStage = Aggregation.match(Criteria.where("status").ne("CANCELLED"));

        // Stage 2: $group by customer_name, computing spending statistics.
        // Use "customer_name" (the MongoDB @Field name), NOT the Java property "customerName".
        // Same rule applies to accumulator field references: "total_amount" not "totalAmount".
        var groupStage = Aggregation.group("customer_name")
                // total amount spent by this customer
                .sum("total_amount").as("totalSpent")
                // number of orders (count all documents in the group)
                .count().as("orderCount")
                // highest single order value
                .max("total_amount").as("maxOrder")
                // lowest single order value
                .min("total_amount").as("minOrder");

        // Stage 3: $project to rename _id → customerName for the result DTO.
        // Place project before sort so the "totalSpent" alias is available.
        var projectStage = Aggregation.project("totalSpent", "orderCount", "maxOrder", "minOrder")
                .and("_id").as("customerName");

        // Stage 4: $sort by totalSpent descending (after project so alias is visible)
        var sortStage = Aggregation.sort(Sort.by(Sort.Direction.DESC, "totalSpent"));

        // Assemble the pipeline: match → group → project → sort
        Aggregation aggregation = Aggregation.newAggregation(
                matchStage, groupStage, projectStage, sortStage);

        AggregationResults<CustomerSummaryResult> results =
                mongoTemplate.aggregate(aggregation, "orders", CustomerSummaryResult.class);

        return results.getMappedResults();
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Aggregation Query 4: Revenue by Region for a Specific Status (compound pipeline)
    // ══════════════════════════════════════════════════════════════════════════════

    /**
     * Compute revenue by region for orders with a specific status.
     *
     * <h3>Key Concept: Compound {@code $match} + {@code $group}</h3>
     * <p>This query demonstrates combining a status filter with the region grouping.
     * It is a "parameterised aggregation" — the caller provides the status value,
     * showing how runtime parameters flow into a MongoDB pipeline.
     *
     * <h3>Aggregation Pipeline</h3>
     * <ol>
     *   <li>{@code $match} – keep only orders with the given status.</li>
     *   <li>{@code $group} – group by region, summing revenue.</li>
     *   <li>{@code $sort}  – order by total revenue descending.</li>
     * </ol>
     *
     * @param status the order status to filter by (e.g. "DELIVERED")
     * @return per-region revenue statistics for orders with the given status
     */
    public List<RevenueByRegionResult> getRevenueByRegionForStatus(String status) {
        // Stage 1: $match – keep only orders matching the requested status
        var matchStage = Aggregation.match(Criteria.where("status").is(status));

        // Stage 2: $group by region with revenue statistics.
        // Use "total_amount" (MongoDB field name), not "totalAmount" (Java property name).
        var groupStage = Aggregation.group("region")
                .sum("total_amount").as("totalRevenue")
                .count().as("orderCount")
                .avg("total_amount").as("avgOrderValue");

        // Stage 3: $project to rename _id → region.
        // Project before sort so the "totalRevenue" alias is visible to $sort.
        var projectStage = Aggregation.project("totalRevenue", "orderCount", "avgOrderValue")
                .and("_id").as("region");

        // Stage 4: $sort by totalRevenue descending
        var sortStage = Aggregation.sort(Sort.by(Sort.Direction.DESC, "totalRevenue"));

        Aggregation aggregation = Aggregation.newAggregation(
                matchStage, groupStage, projectStage, sortStage);

        AggregationResults<RevenueByRegionResult> results =
                mongoTemplate.aggregate(aggregation, "orders", RevenueByRegionResult.class);

        return results.getMappedResults();
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Aggregation Query 5: Orders above a minimum amount using MongoTemplate Query
    // ══════════════════════════════════════════════════════════════════════════════

    /**
     * Retrieve orders whose total amount is at or above a given minimum.
     *
     * <p>This method uses {@link MongoTemplate#find(Query, Class)} with a
     * {@link Criteria} filter rather than an aggregation pipeline. It illustrates
     * the simpler {@code MongoTemplate} query API (as opposed to aggregation) for
     * cases where grouping/unwinding are not needed.
     *
     * <p>Equivalent MongoDB shell query:
     * <pre>
     * db.orders.find({ total_amount: { $gte: minAmount } })
     *          .sort({ total_amount: -1 })
     * </pre>
     *
     * @param minAmount minimum total amount (inclusive)
     * @return list of orders with total amount at or above {@code minAmount},
     *         sorted by total amount descending
     */
    public List<Order> findOrdersAboveAmount(BigDecimal minAmount) {
        // Criteria.where("totalAmount").gte(minAmount) → { total_amount: { $gte: <value> } }
        // Spring Data maps "totalAmount" to the @Field("total_amount") MongoDB field name.
        Query query = new Query(Criteria.where("totalAmount").gte(minAmount))
                .with(Sort.by(Sort.Direction.DESC, "totalAmount"));
        return mongoTemplate.find(query, Order.class);
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Aggregation Query 6: Order status distribution
    // ══════════════════════════════════════════════════════════════════════════════

    /**
     * Count orders grouped by status to get the distribution of order states.
     *
     * <h3>Aggregation Pipeline</h3>
     * <ol>
     *   <li>{@code $group} – group by {@code status}, counting documents in each group.</li>
     *   <li>{@code $sort}  – order by {@code count} descending.</li>
     * </ol>
     *
     * <h3>Equivalent MongoDB shell query</h3>
     * <pre>
     * db.orders.aggregate([
     *   { $group: { _id: "$status", count: { $sum: 1 } } },
     *   { $sort: { count: -1 } }
     * ])
     * </pre>
     *
     * <p>The result is a list of maps. Each map has two entries:
     * {@code "status"} (string) and {@code "count"} (number).
     *
     * @return list of status-count pairs sorted by count descending
     */
    public List<org.bson.Document> getOrderStatusDistribution() {
        // Stage 1: $group by status, counting documents per status
        var groupStage = Aggregation.group("status")
                .count().as("count");

        // Stage 2: $project to rename _id → status.
        // Project before sort so the "count" alias is available to $sort.
        var projectStage = Aggregation.project("count")
                .and("_id").as("status");

        // Stage 3: $sort by count descending
        var sortStage = Aggregation.sort(Sort.by(Sort.Direction.DESC, "count"));

        Aggregation aggregation = Aggregation.newAggregation(groupStage, projectStage, sortStage);

        // Map to raw BSON Document since this is a simple key-value result
        AggregationResults<org.bson.Document> results =
                mongoTemplate.aggregate(aggregation, "orders", org.bson.Document.class);

        return results.getMappedResults();
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Aggregation Query 7: Monthly revenue (with $project date operators)
    // ══════════════════════════════════════════════════════════════════════════════

    /**
     * Compute total revenue grouped by year and month.
     *
     * <h3>Key Concept: Date operators in {@code $project}</h3>
     * <p>MongoDB provides date extraction operators ({@code $year}, {@code $month},
     * {@code $dayOfMonth}) that work on date fields. This pipeline extracts year
     * and month from {@code created_at}, then groups on those two fields to produce
     * a monthly time series of revenue.
     *
     * <h3>Aggregation Pipeline</h3>
     * <ol>
     *   <li>{@code $project} – add computed {@code year} and {@code month} fields.</li>
     *   <li>{@code $group}   – group by {@code {year, month}}, summing revenue.</li>
     *   <li>{@code $sort}    – order chronologically (year asc, month asc).</li>
     * </ol>
     *
     * <h3>Equivalent MongoDB shell query</h3>
     * <pre>
     * db.orders.aggregate([
     *   { $project: {
     *       year:        { $year:  "$created_at" },
     *       month:       { $month: "$created_at" },
     *       total_amount: 1
     *   }},
     *   { $group: {
     *       _id: { year: "$year", month: "$month" },
     *       totalRevenue: { $sum: "$total_amount" },
     *       orderCount:   { $sum: 1 }
     *   }},
     *   { $sort: { "_id.year": 1, "_id.month": 1 } }
     * ])
     * </pre>
     *
     * @return list of BSON Documents with {@code year}, {@code month}, {@code totalRevenue},
     *         and {@code orderCount} fields, ordered chronologically
     */
    public List<org.bson.Document> getMonthlyRevenue() {
        // Stage 1: $project to extract year and month from the createdAt timestamp.
        // DateOperators.Year.yearOf("createdAt") correctly maps to { $year: "$created_at" }
        // because the $project stage uses the MappingMongoConverter field name translation.
        // Also carry "total_amount" through (using MongoDB field name) for the group stage.
        var projectStage = Aggregation.project("total_amount")
                .and(org.springframework.data.mongodb.core.aggregation.DateOperators
                        .Year.yearOf("createdAt")).as("year")
                .and(org.springframework.data.mongodb.core.aggregation.DateOperators
                        .Month.monthOf("createdAt")).as("month");

        // Stage 2: $group by { year, month } compound key, summing revenue.
        // After the $project stage, the field is named "total_amount" (passed through).
        var groupStage = Aggregation.group("year", "month")
                .sum("total_amount").as("totalRevenue")
                .count().as("orderCount");

        // Stage 3: $sort chronologically by the compound _id (year then month)
        var sortStage = Aggregation.sort(
                Sort.by(Sort.Direction.ASC, "_id.year")
                        .and(Sort.by(Sort.Direction.ASC, "_id.month")));

        Aggregation aggregation = Aggregation.newAggregation(projectStage, groupStage, sortStage);

        // Map to raw BSON Document to preserve the compound _id structure
        AggregationResults<org.bson.Document> results =
                mongoTemplate.aggregate(aggregation, "orders", org.bson.Document.class);

        return results.getMappedResults();
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Aggregation Query 8: Category revenue breakdown (unwind + match + group)
    // ══════════════════════════════════════════════════════════════════════════════

    /**
     * Compute total revenue and units sold per product category.
     *
     * <h3>Key Concept: Filtering after {@code $unwind}</h3>
     * <p>After unwinding the items array, a second {@code $match} stage can filter
     * individual items by their fields (e.g. only items in a specific category).
     * This is a common pattern: {@code $match} → {@code $unwind} → {@code $match}
     * → {@code $group}, where the first match reduces the number of parent documents
     * and the second match filters items within the unwound documents.
     *
     * <h3>Aggregation Pipeline</h3>
     * <ol>
     *   <li>{@code $unwind} – explode the items array.</li>
     *   <li>{@code $group}  – group by {@code items.category}, computing revenue and quantity.</li>
     *   <li>{@code $sort}   – order by total revenue descending.</li>
     * </ol>
     *
     * @return per-category revenue and unit statistics, sorted by revenue descending
     */
    public List<org.bson.Document> getRevenueByCategoryBreakdown() {
        // Stage 1: $unwind the items array
        var unwindStage = Aggregation.unwind("items");

        // Stage 2: $group by item category.
        // After $unwind, nested fields are accessed via their MongoDB names: "items.category",
        // "items.unit_price", "items.quantity" — NOT the Java property names.
        var groupStage = Aggregation.group("items.category")
                .sum(ArithmeticOperators.Multiply.valueOf("items.unit_price")
                        .multiplyBy("items.quantity")).as("totalRevenue")
                .sum("items.quantity").as("totalQuantity");

        // Stage 3: $project to rename _id → category.
        // Project before sort so "totalRevenue" alias is visible.
        var projectStage = Aggregation.project("totalRevenue", "totalQuantity")
                .and("_id").as("category");

        // Stage 4: $sort by totalRevenue descending
        var sortStage = Aggregation.sort(Sort.by(Sort.Direction.DESC, "totalRevenue"));

        Aggregation aggregation = Aggregation.newAggregation(
                unwindStage, groupStage, projectStage, sortStage);

        AggregationResults<org.bson.Document> results =
                mongoTemplate.aggregate(aggregation, "orders", org.bson.Document.class);

        return results.getMappedResults();
    }

    // ══════════════════════════════════════════════════════════════════════════════
    // Aggregation Query 9: High-value orders in a time range
    // ══════════════════════════════════════════════════════════════════════════════

    /**
     * Find high-value orders (above a minimum amount) placed within a time range.
     *
     * <h3>Key Concept: Multiple criteria in a single {@code $match} stage</h3>
     * <p>The {@link Criteria} builder supports combining multiple conditions with
     * logical operators. {@code Criteria.where("field1").gte(v1).and("field2").gte(v2)}
     * produces a MongoDB {@code $and} condition implicitly.
     *
     * <p>Equivalent MongoDB shell query:
     * <pre>
     * db.orders.find({
     *   created_at:   { $gte: ISODate("startDate"), $lte: ISODate("endDate") },
     *   total_amount: { $gte: minAmount }
     * }).sort({ total_amount: -1 })
     * </pre>
     *
     * @param start     the start of the time range (inclusive)
     * @param end       the end of the time range (inclusive)
     * @param minAmount minimum total amount filter (inclusive)
     * @return orders matching both the time range and the minimum amount filter,
     *         sorted by total amount descending
     */
    public List<Order> findHighValueOrdersInTimeRange(Instant start, Instant end, BigDecimal minAmount) {
        // Build a compound Criteria:
        // { created_at: { $gte: start, $lte: end }, total_amount: { $gte: minAmount } }
        Criteria criteria = Criteria.where("createdAt").gte(start).lte(end)
                .and("totalAmount").gte(minAmount);

        Query query = new Query(criteria)
                .with(Sort.by(Sort.Direction.DESC, "totalAmount"));

        return mongoTemplate.find(query, Order.class);
    }
}
