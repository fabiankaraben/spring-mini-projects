package com.example.mongodbcustomqueries.controller;

import com.example.mongodbcustomqueries.domain.Order;
import com.example.mongodbcustomqueries.dto.CustomerSummaryResult;
import com.example.mongodbcustomqueries.dto.OrderRequest;
import com.example.mongodbcustomqueries.dto.RevenueByRegionResult;
import com.example.mongodbcustomqueries.dto.TopProductResult;
import com.example.mongodbcustomqueries.service.OrderAggregationService;
import jakarta.validation.Valid;
import org.bson.Document;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * REST controller exposing order CRUD and aggregation analytics endpoints.
 *
 * <p>All business logic and aggregation pipeline construction are delegated to
 * {@link OrderAggregationService}. This controller is responsible only for:
 * <ul>
 *   <li>Mapping HTTP verbs and URL paths to service methods.</li>
 *   <li>Triggering Bean Validation via {@code @Valid} on request bodies.</li>
 *   <li>Converting service results to the appropriate HTTP status codes and
 *       response bodies.</li>
 * </ul>
 *
 * <p>Base path: {@code /api/orders}
 *
 * <p>Endpoints summary:
 * <pre>
 *   POST   /api/orders                              – create a new order
 *   GET    /api/orders                              – list all orders
 *   GET    /api/orders/{id}                         – get order by ID
 *   DELETE /api/orders/{id}                         – delete an order
 *   GET    /api/orders/above-amount?min=...         – orders above a minimum amount
 *   GET    /api/orders/high-value?start=&end=&min=  – high-value orders in time range
 *
 *   -- Aggregation endpoints --
 *   GET    /api/orders/analytics/revenue-by-region          – revenue grouped by region
 *   GET    /api/orders/analytics/revenue-by-region/{status} – revenue by region + status filter
 *   GET    /api/orders/analytics/top-products?topN=...      – top products by revenue
 *   GET    /api/orders/analytics/customer-summary           – customer spending summary
 *   GET    /api/orders/analytics/status-distribution        – order count per status
 *   GET    /api/orders/analytics/monthly-revenue            – revenue grouped by year/month
 *   GET    /api/orders/analytics/category-breakdown         – revenue per product category
 * </pre>
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderAggregationService orderService;

    /**
     * Constructor injection makes the dependency explicit and simplifies testing
     * (no Spring context needed to instantiate this controller in unit tests).
     *
     * @param orderService the service containing CRUD and aggregation logic
     */
    public OrderController(OrderAggregationService orderService) {
        this.orderService = orderService;
    }

    // ── POST /api/orders ──────────────────────────────────────────────────────────

    /**
     * Create a new order document in MongoDB.
     *
     * <p>{@code @Valid} triggers Bean Validation on the request body. Invalid
     * requests result in a 400 Bad Request response.
     *
     * @param request the order data from the HTTP request body (JSON)
     * @return 201 Created with the persisted order (including its generated ID)
     */
    @PostMapping
    public ResponseEntity<Order> createOrder(@Valid @RequestBody OrderRequest request) {
        Order created = orderService.create(request);
        // HTTP 201 Created is the semantically correct status for a successful POST
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    // ── GET /api/orders ───────────────────────────────────────────────────────────

    /**
     * List all orders in the MongoDB collection.
     *
     * @return 200 OK with a JSON array of all orders
     */
    @GetMapping
    public ResponseEntity<List<Order>> getAllOrders() {
        return ResponseEntity.ok(orderService.findAll());
    }

    // ── GET /api/orders/{id} ──────────────────────────────────────────────────────

    /**
     * Get a single order by its MongoDB ObjectId.
     *
     * @param id the order's MongoDB document ID (24-character hex string)
     * @return 200 OK with the order, or 404 Not Found if the ID doesn't exist
     */
    @GetMapping("/{id}")
    public ResponseEntity<Order> getOrderById(@PathVariable String id) {
        Optional<Order> order = orderService.findById(id);
        return order.map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    // ── DELETE /api/orders/{id} ───────────────────────────────────────────────────

    /**
     * Delete an order document by its MongoDB ObjectId.
     *
     * @param id the order's MongoDB document ID
     * @return 204 No Content on success, or 404 Not Found if the ID doesn't exist
     */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteOrder(@PathVariable String id) {
        boolean deleted = orderService.deleteById(id);
        if (!deleted) {
            return ResponseEntity.notFound().build();
        }
        // HTTP 204 No Content is the standard response for a successful DELETE
        return ResponseEntity.noContent().build();
    }

    // ── GET /api/orders/above-amount ──────────────────────────────────────────────

    /**
     * List orders with total amount at or above the given minimum.
     *
     * <p>Uses {@code MongoTemplate} with a {@code Criteria.gte()} filter.
     * Example: {@code GET /api/orders/above-amount?min=500.00}
     *
     * @param min the minimum total amount (inclusive)
     * @return 200 OK with matching orders sorted by amount descending
     */
    @GetMapping("/above-amount")
    public ResponseEntity<List<Order>> getOrdersAboveAmount(@RequestParam BigDecimal min) {
        return ResponseEntity.ok(orderService.findOrdersAboveAmount(min));
    }

    // ── GET /api/orders/high-value ────────────────────────────────────────────────

    /**
     * Find high-value orders within a time range.
     *
     * <p>Uses compound {@code Criteria} to filter by both time range and minimum amount.
     * Example: {@code GET /api/orders/high-value?start=2024-01-01T00:00:00Z&end=2024-12-31T23:59:59Z&min=1000.00}
     *
     * @param start the start of the time range (ISO-8601 instant, e.g. {@code 2024-01-01T00:00:00Z})
     * @param end   the end of the time range (ISO-8601 instant)
     * @param min   minimum total amount filter
     * @return 200 OK with matching high-value orders sorted by amount descending
     */
    @GetMapping("/high-value")
    public ResponseEntity<List<Order>> getHighValueOrders(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant start,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant end,
            @RequestParam BigDecimal min) {
        return ResponseEntity.ok(orderService.findHighValueOrdersInTimeRange(start, end, min));
    }

    // ── GET /api/orders/analytics/revenue-by-region ───────────────────────────────

    /**
     * Aggregation: total revenue, order count, and average order value per region.
     *
     * <p>Pipeline: {@code $group} by region → {@code $sort} by revenue desc.
     *
     * @return 200 OK with per-region revenue statistics
     */
    @GetMapping("/analytics/revenue-by-region")
    public ResponseEntity<List<RevenueByRegionResult>> getRevenueByRegion() {
        return ResponseEntity.ok(orderService.getRevenueByRegion());
    }

    // ── GET /api/orders/analytics/revenue-by-region/{status} ─────────────────────

    /**
     * Aggregation: revenue by region filtered to a specific order status.
     *
     * <p>Pipeline: {@code $match} by status → {@code $group} by region → {@code $sort}.
     * Example: {@code GET /api/orders/analytics/revenue-by-region/DELIVERED}
     *
     * @param status the order status to filter by (e.g. "DELIVERED", "SHIPPED")
     * @return 200 OK with per-region revenue statistics for the given status
     */
    @GetMapping("/analytics/revenue-by-region/{status}")
    public ResponseEntity<List<RevenueByRegionResult>> getRevenueByRegionForStatus(
            @PathVariable String status) {
        return ResponseEntity.ok(orderService.getRevenueByRegionForStatus(status));
    }

    // ── GET /api/orders/analytics/top-products ────────────────────────────────────

    /**
     * Aggregation: top-N products by total revenue generated.
     *
     * <p>Pipeline: {@code $unwind} items → {@code $group} by product → {@code $sort}
     * by revenue desc → {@code $limit}.
     * Example: {@code GET /api/orders/analytics/top-products?topN=5}
     *
     * @param topN the number of top products to return (default: 5)
     * @return 200 OK with the top-N products ranked by revenue
     */
    @GetMapping("/analytics/top-products")
    public ResponseEntity<List<TopProductResult>> getTopProducts(
            @RequestParam(defaultValue = "5") int topN) {
        return ResponseEntity.ok(orderService.getTopProductsByRevenue(topN));
    }

    // ── GET /api/orders/analytics/customer-summary ────────────────────────────────

    /**
     * Aggregation: customer spending summary excluding cancelled orders.
     *
     * <p>Pipeline: {@code $match} status ≠ CANCELLED → {@code $group} by customer
     * → {@code $sort} by total spent desc.
     *
     * @return 200 OK with per-customer spending statistics
     */
    @GetMapping("/analytics/customer-summary")
    public ResponseEntity<List<CustomerSummaryResult>> getCustomerSummary() {
        return ResponseEntity.ok(orderService.getCustomerSpendingSummary());
    }

    // ── GET /api/orders/analytics/status-distribution ────────────────────────────

    /**
     * Aggregation: count of orders per status (status distribution).
     *
     * <p>Pipeline: {@code $group} by status → {@code $sort} by count desc.
     *
     * @return 200 OK with a list of {@code {status, count}} documents
     */
    @GetMapping("/analytics/status-distribution")
    public ResponseEntity<List<Document>> getStatusDistribution() {
        return ResponseEntity.ok(orderService.getOrderStatusDistribution());
    }

    // ── GET /api/orders/analytics/monthly-revenue ─────────────────────────────────

    /**
     * Aggregation: total revenue grouped by year and month.
     *
     * <p>Pipeline: {@code $project} date parts → {@code $group} by year+month
     * → {@code $sort} chronologically.
     *
     * @return 200 OK with monthly revenue time series
     */
    @GetMapping("/analytics/monthly-revenue")
    public ResponseEntity<List<Document>> getMonthlyRevenue() {
        return ResponseEntity.ok(orderService.getMonthlyRevenue());
    }

    // ── GET /api/orders/analytics/category-breakdown ──────────────────────────────

    /**
     * Aggregation: total revenue and units sold per product category.
     *
     * <p>Pipeline: {@code $unwind} items → {@code $group} by category
     * → {@code $sort} by revenue desc.
     *
     * @return 200 OK with per-category revenue breakdown
     */
    @GetMapping("/analytics/category-breakdown")
    public ResponseEntity<List<Document>> getCategoryBreakdown() {
        return ResponseEntity.ok(orderService.getRevenueByCategoryBreakdown());
    }
}
