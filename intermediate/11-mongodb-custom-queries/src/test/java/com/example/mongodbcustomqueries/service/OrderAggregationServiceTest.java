package com.example.mongodbcustomqueries.service;

import com.example.mongodbcustomqueries.domain.Order;
import com.example.mongodbcustomqueries.domain.OrderItem;
import com.example.mongodbcustomqueries.dto.CustomerSummaryResult;
import com.example.mongodbcustomqueries.dto.OrderRequest;
import com.example.mongodbcustomqueries.dto.RevenueByRegionResult;
import com.example.mongodbcustomqueries.dto.TopProductResult;
import com.example.mongodbcustomqueries.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.aggregation.Aggregation;
import org.springframework.data.mongodb.core.aggregation.AggregationResults;
import org.springframework.data.mongodb.core.query.Query;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OrderAggregationService}.
 *
 * <p>These tests exercise the service's business logic in pure isolation:
 * <ul>
 *   <li>The {@link OrderRepository} is replaced with a Mockito mock — no MongoDB
 *       connection is needed. Tests run in milliseconds without Docker.</li>
 *   <li>The {@link MongoTemplate} is also mocked so that aggregation pipeline
 *       execution is simulated via {@code when(...).thenReturn(...)}.</li>
 *   <li>No Spring context is loaded — {@link ExtendWith}({@link MockitoExtension}.class)
 *       initialises Mockito annotations only, keeping startup time near zero.</li>
 *   <li>Each test follows the Given / When / Then (Arrange / Act / Assert) pattern
 *       to make intent and expectations explicit for educational clarity.</li>
 * </ul>
 *
 * <p>Integration tests (see {@link com.example.mongodbcustomqueries.OrderIntegrationTest})
 * cover the full stack including real aggregation pipelines against a MongoDB container
 * managed by Testcontainers.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrderAggregationService unit tests")
class OrderAggregationServiceTest {

    /**
     * Mockito mock of the repository — no real MongoDB involved.
     * All interactions with MongoDB are simulated via stubs.
     */
    @Mock
    private OrderRepository orderRepository;

    /**
     * Mockito mock of {@link MongoTemplate}.
     * Required because {@link OrderAggregationService} uses MongoTemplate for
     * both aggregation pipelines and Criteria-based queries.
     * {@code @InjectMocks} will inject this mock alongside the repository mock.
     */
    @Mock
    private MongoTemplate mongoTemplate;

    /**
     * The class under test.
     * {@code @InjectMocks} asks Mockito to create an {@link OrderAggregationService}
     * instance and inject all {@code @Mock} fields into it via constructor injection.
     */
    @InjectMocks
    private OrderAggregationService service;

    // ── Shared test fixtures ──────────────────────────────────────────────────────

    /** A pre-built order returned by the mock repository in tests. */
    private Order sampleOrder;

    /** A DTO that would arrive in an HTTP POST request body. */
    private OrderRequest sampleRequest;

    @BeforeEach
    void setUp() {
        // Build sample items embedded in the order
        List<OrderItem> items = List.of(
                new OrderItem("Laptop", "electronics", 1, new BigDecimal("999.99")),
                new OrderItem("Mouse", "electronics", 2, new BigDecimal("29.99"))
        );

        // Build a sample order that the mock repository will return
        sampleOrder = new Order("Alice", "North", "DELIVERED",
                new BigDecimal("1059.97"), items);
        sampleOrder.setId("507f1f77bcf86cd799439011");

        // Build a corresponding request DTO
        sampleRequest = new OrderRequest("Alice", "North", "DELIVERED",
                new BigDecimal("1059.97"), items);
    }

    // ── create ────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("create maps the request DTO to an Order and persists it via repository")
    void create_persistsOrderAndReturnsWithGeneratedId() {
        // Given: the repository assigns a MongoDB ObjectId when saving
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> {
            Order o = invocation.getArgument(0);
            o.setId("507f1f77bcf86cd799439011"); // simulate MongoDB-generated ObjectId
            return o;
        });

        // When: create via the service
        Order created = service.create(sampleRequest);

        // Then: the returned order has an assigned ID and matches the request data
        assertThat(created).isNotNull();
        assertThat(created.getId()).isEqualTo("507f1f77bcf86cd799439011");
        assertThat(created.getCustomerName()).isEqualTo(sampleRequest.getCustomerName());
        assertThat(created.getRegion()).isEqualTo(sampleRequest.getRegion());
        assertThat(created.getStatus()).isEqualTo(sampleRequest.getStatus());
        assertThat(created.getTotalAmount()).isEqualByComparingTo(sampleRequest.getTotalAmount());
        assertThat(created.getItems()).hasSize(2);
        // createdAt is set in the constructor; it must not be null
        assertThat(created.getCreatedAt()).isNotNull();

        verify(orderRepository, times(1)).save(any(Order.class));
    }

    // ── findAll ───────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("findAll returns all orders from the repository")
    void findAll_returnsAllOrders() {
        // Given: the repository holds two orders
        Order second = new Order("Bob", "South", "PENDING",
                new BigDecimal("199.99"), List.of());
        when(orderRepository.findAll()).thenReturn(List.of(sampleOrder, second));

        // When
        List<Order> results = service.findAll();

        // Then: both orders are returned and the repository was called exactly once
        assertThat(results).hasSize(2);
        assertThat(results).extracting(Order::getCustomerName)
                .containsExactlyInAnyOrder("Alice", "Bob");
        verify(orderRepository, times(1)).findAll();
    }

    @Test
    @DisplayName("findAll returns empty list when the collection is empty")
    void findAll_returnsEmptyList_whenCollectionIsEmpty() {
        // Given: no documents in the collection
        when(orderRepository.findAll()).thenReturn(List.of());

        // When
        List<Order> results = service.findAll();

        // Then: an empty list is returned (never null)
        assertThat(results).isNotNull().isEmpty();
        verify(orderRepository, times(1)).findAll();
    }

    // ── findById ──────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("findById returns the order wrapped in Optional when it exists")
    void findById_returnsOrder_whenExists() {
        // Given: the repository finds a document for the given ID
        when(orderRepository.findById("507f1f77bcf86cd799439011"))
                .thenReturn(Optional.of(sampleOrder));

        // When
        Optional<Order> result = service.findById("507f1f77bcf86cd799439011");

        // Then: the order is present with the correct data
        assertThat(result).isPresent();
        assertThat(result.get().getId()).isEqualTo("507f1f77bcf86cd799439011");
        assertThat(result.get().getCustomerName()).isEqualTo("Alice");
        verify(orderRepository, times(1)).findById("507f1f77bcf86cd799439011");
    }

    @Test
    @DisplayName("findById returns empty Optional when the order does not exist")
    void findById_returnsEmpty_whenNotFound() {
        // Given: no order with the given ID
        when(orderRepository.findById("nonexistent-id")).thenReturn(Optional.empty());

        // When
        Optional<Order> result = service.findById("nonexistent-id");

        // Then: the Optional is empty
        assertThat(result).isEmpty();
        verify(orderRepository, times(1)).findById("nonexistent-id");
    }

    // ── deleteById ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("deleteById returns true when the order exists and is deleted")
    void deleteById_returnsTrue_whenExists() {
        // Given: the order exists
        when(orderRepository.existsById("507f1f77bcf86cd799439011")).thenReturn(true);

        // When
        boolean result = service.deleteById("507f1f77bcf86cd799439011");

        // Then: true is returned and deleteById was called on the repository
        assertThat(result).isTrue();
        verify(orderRepository, times(1)).existsById("507f1f77bcf86cd799439011");
        verify(orderRepository, times(1)).deleteById("507f1f77bcf86cd799439011");
    }

    @Test
    @DisplayName("deleteById returns false and never calls deleteById when the order does not exist")
    void deleteById_returnsFalse_whenNotFound() {
        // Given: no order with the given ID
        when(orderRepository.existsById("nonexistent-id")).thenReturn(false);

        // When
        boolean result = service.deleteById("nonexistent-id");

        // Then: false is returned and deleteById was never called (nothing to delete)
        assertThat(result).isFalse();
        verify(orderRepository, times(1)).existsById("nonexistent-id");
        verify(orderRepository, never()).deleteById(any());
    }

    // ── findOrdersAboveAmount ─────────────────────────────────────────────────────

    @Test
    @DisplayName("findOrdersAboveAmount delegates to MongoTemplate with the correct Criteria")
    void findOrdersAboveAmount_delegatesToMongoTemplate() {
        // Given: MongoTemplate returns two matching orders
        Order highValue = new Order("Charlie", "West", "SHIPPED",
                new BigDecimal("2000.00"), List.of());
        when(mongoTemplate.find(any(Query.class), eq(Order.class)))
                .thenReturn(List.of(sampleOrder, highValue));

        // When
        List<Order> results = service.findOrdersAboveAmount(new BigDecimal("500.00"));

        // Then: the results are returned and MongoTemplate was called once
        assertThat(results).hasSize(2);
        verify(mongoTemplate, times(1)).find(any(Query.class), eq(Order.class));
    }

    // ── findHighValueOrdersInTimeRange ────────────────────────────────────────────

    @Test
    @DisplayName("findHighValueOrdersInTimeRange delegates to MongoTemplate with compound Criteria")
    void findHighValueOrdersInTimeRange_delegatesToMongoTemplate() {
        // Given: MongoTemplate returns one matching high-value order
        when(mongoTemplate.find(any(Query.class), eq(Order.class)))
                .thenReturn(List.of(sampleOrder));

        Instant start = Instant.parse("2024-01-01T00:00:00Z");
        Instant end   = Instant.parse("2024-12-31T23:59:59Z");

        // When
        List<Order> results = service.findHighValueOrdersInTimeRange(
                start, end, new BigDecimal("500.00"));

        // Then
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getCustomerName()).isEqualTo("Alice");
        verify(mongoTemplate, times(1)).find(any(Query.class), eq(Order.class));
    }

    // ── getRevenueByRegion ────────────────────────────────────────────────────────

    @Test
    @DisplayName("getRevenueByRegion returns aggregation results from MongoTemplate")
    void getRevenueByRegion_returnsAggregationResults() {
        // Given: stub the aggregation result
        RevenueByRegionResult northResult = new RevenueByRegionResult(
                "North", new BigDecimal("5000.00"), 5L, new BigDecimal("1000.00"));
        RevenueByRegionResult southResult = new RevenueByRegionResult(
                "South", new BigDecimal("3000.00"), 3L, new BigDecimal("1000.00"));

        // Use a mocked AggregationResults wrapper
        @SuppressWarnings("unchecked")
        AggregationResults<RevenueByRegionResult> mockResults =
                mock(AggregationResults.class);
        when(mockResults.getMappedResults()).thenReturn(List.of(northResult, southResult));
        when(mongoTemplate.aggregate(any(Aggregation.class), eq("orders"),
                eq(RevenueByRegionResult.class))).thenReturn(mockResults);

        // When
        List<RevenueByRegionResult> results = service.getRevenueByRegion();

        // Then: both region results are returned
        assertThat(results).hasSize(2);
        assertThat(results).extracting(RevenueByRegionResult::getRegion)
                .containsExactlyInAnyOrder("North", "South");
        assertThat(results.get(0).getTotalRevenue()).isEqualByComparingTo("5000.00");
        verify(mongoTemplate, times(1)).aggregate(
                any(Aggregation.class), eq("orders"), eq(RevenueByRegionResult.class));
    }

    @Test
    @DisplayName("getRevenueByRegion returns empty list when no orders exist")
    void getRevenueByRegion_returnsEmptyList_whenNoOrders() {
        // Given: the aggregation returns no results
        @SuppressWarnings("unchecked")
        AggregationResults<RevenueByRegionResult> mockResults =
                mock(AggregationResults.class);
        when(mockResults.getMappedResults()).thenReturn(List.of());
        when(mongoTemplate.aggregate(any(Aggregation.class), eq("orders"),
                eq(RevenueByRegionResult.class))).thenReturn(mockResults);

        // When
        List<RevenueByRegionResult> results = service.getRevenueByRegion();

        // Then: empty list is returned
        assertThat(results).isEmpty();
    }

    // ── getTopProductsByRevenue ───────────────────────────────────────────────────

    @Test
    @DisplayName("getTopProductsByRevenue returns top-N products from aggregation")
    void getTopProductsByRevenue_returnsTopNProducts() {
        // Given: two products in the aggregation result
        TopProductResult laptop = new TopProductResult(
                "Laptop", new BigDecimal("9999.00"), 10L);
        TopProductResult mouse  = new TopProductResult(
                "Mouse", new BigDecimal("599.80"), 20L);

        @SuppressWarnings("unchecked")
        AggregationResults<TopProductResult> mockResults = mock(AggregationResults.class);
        when(mockResults.getMappedResults()).thenReturn(List.of(laptop, mouse));
        when(mongoTemplate.aggregate(any(Aggregation.class), eq("orders"),
                eq(TopProductResult.class))).thenReturn(mockResults);

        // When: request top 2 products
        List<TopProductResult> results = service.getTopProductsByRevenue(2);

        // Then: both products are returned with correct data
        assertThat(results).hasSize(2);
        assertThat(results.get(0).getProductName()).isEqualTo("Laptop");
        assertThat(results.get(0).getTotalRevenue()).isEqualByComparingTo("9999.00");
        assertThat(results.get(0).getTotalQuantity()).isEqualTo(10L);
        verify(mongoTemplate, times(1)).aggregate(
                any(Aggregation.class), eq("orders"), eq(TopProductResult.class));
    }

    // ── getCustomerSpendingSummary ────────────────────────────────────────────────

    @Test
    @DisplayName("getCustomerSpendingSummary returns customer data excluding cancelled orders")
    void getCustomerSpendingSummary_returnsCustomerData() {
        // Given: two customers in the aggregation result
        CustomerSummaryResult alice = new CustomerSummaryResult(
                "Alice", new BigDecimal("5000.00"), 5L,
                new BigDecimal("1500.00"), new BigDecimal("200.00"));
        CustomerSummaryResult bob = new CustomerSummaryResult(
                "Bob", new BigDecimal("2000.00"), 2L,
                new BigDecimal("1200.00"), new BigDecimal("800.00"));

        @SuppressWarnings("unchecked")
        AggregationResults<CustomerSummaryResult> mockResults =
                mock(AggregationResults.class);
        when(mockResults.getMappedResults()).thenReturn(List.of(alice, bob));
        when(mongoTemplate.aggregate(any(Aggregation.class), eq("orders"),
                eq(CustomerSummaryResult.class))).thenReturn(mockResults);

        // When
        List<CustomerSummaryResult> results = service.getCustomerSpendingSummary();

        // Then: both customer summaries are returned
        assertThat(results).hasSize(2);
        assertThat(results.get(0).getCustomerName()).isEqualTo("Alice");
        assertThat(results.get(0).getTotalSpent()).isEqualByComparingTo("5000.00");
        assertThat(results.get(0).getOrderCount()).isEqualTo(5L);
        assertThat(results.get(0).getMaxOrder()).isEqualByComparingTo("1500.00");
        verify(mongoTemplate, times(1)).aggregate(
                any(Aggregation.class), eq("orders"), eq(CustomerSummaryResult.class));
    }

    // ── getRevenueByRegionForStatus ───────────────────────────────────────────────

    @Test
    @DisplayName("getRevenueByRegionForStatus filters by status and returns region results")
    void getRevenueByRegionForStatus_returnsFilteredResults() {
        // Given: one region result for DELIVERED orders
        RevenueByRegionResult northDelivered = new RevenueByRegionResult(
                "North", new BigDecimal("3000.00"), 3L, new BigDecimal("1000.00"));

        @SuppressWarnings("unchecked")
        AggregationResults<RevenueByRegionResult> mockResults =
                mock(AggregationResults.class);
        when(mockResults.getMappedResults()).thenReturn(List.of(northDelivered));
        when(mongoTemplate.aggregate(any(Aggregation.class), eq("orders"),
                eq(RevenueByRegionResult.class))).thenReturn(mockResults);

        // When
        List<RevenueByRegionResult> results = service.getRevenueByRegionForStatus("DELIVERED");

        // Then
        assertThat(results).hasSize(1);
        assertThat(results.get(0).getRegion()).isEqualTo("North");
        assertThat(results.get(0).getTotalRevenue()).isEqualByComparingTo("3000.00");
        verify(mongoTemplate, times(1)).aggregate(
                any(Aggregation.class), eq("orders"), eq(RevenueByRegionResult.class));
    }
}
