package com.example.grpcclient.service;

import com.example.grpcclient.domain.Order;
import com.example.grpcclient.domain.OrderLineItem;
import com.example.grpcclient.domain.OrderStatus;
import com.example.grpcclient.repository.OrderRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link OrderService}.
 *
 * <p>These tests focus purely on the business logic in the service layer,
 * without starting a Spring context, a gRPC server, or a database.
 *
 * <p>Testing strategy:
 * <ul>
 *   <li>{@link ExtendWith(MockitoExtension.class)} — uses the Mockito JUnit 5 extension
 *       to initialize mocks and inject them.</li>
 *   <li>{@link Mock} — creates a mock of {@link OrderRepository} to control
 *       its behaviour without hitting a real database.</li>
 *   <li>{@link InjectMocks} — creates a real {@link OrderService} with the mocked
 *       repository injected via constructor injection.</li>
 *   <li>Nested test classes group related scenarios for readability.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrderService Unit Tests")
class OrderServiceTest {

    /**
     * Mocked repository — all method calls are intercepted by Mockito.
     */
    @Mock
    private OrderRepository orderRepository;

    /**
     * The class under test. Mockito injects the mocked repository via constructor.
     */
    @InjectMocks
    private OrderService orderService;

    // =========================================================================
    // Tests for findById()
    // =========================================================================

    @Nested
    @DisplayName("findById()")
    class FindById {

        @Test
        @DisplayName("returns the order when it exists")
        void returnsOrderWhenFound() {
            // Given: an order exists in the repository.
            Order order = buildOrder("order-001", "customer-A", OrderStatus.PENDING, 199.99);
            when(orderRepository.findById("order-001")).thenReturn(Optional.of(order));

            // When: we look up the order.
            Optional<Order> result = orderService.findById("order-001");

            // Then: the Optional contains the expected order.
            assertThat(result).isPresent();
            assertThat(result.get().getOrderId()).isEqualTo("order-001");
            assertThat(result.get().getCustomerId()).isEqualTo("customer-A");
        }

        @Test
        @DisplayName("returns empty Optional when order does not exist")
        void returnsEmptyWhenNotFound() {
            // Given: no order with this ID.
            when(orderRepository.findById("not-exist")).thenReturn(Optional.empty());

            // When: we look up a non-existent order.
            Optional<Order> result = orderService.findById("not-exist");

            // Then: the result is empty.
            assertThat(result).isEmpty();
        }
    }

    // =========================================================================
    // Tests for findByCustomerId()
    // =========================================================================

    @Nested
    @DisplayName("findByCustomerId()")
    class FindByCustomerId {

        @Test
        @DisplayName("returns all orders for the given customer")
        void returnsOrdersForCustomer() {
            // Given: two orders for customer-B.
            List<Order> orders = List.of(
                    buildOrder("order-100", "customer-B", OrderStatus.CONFIRMED, 500.00),
                    buildOrder("order-101", "customer-B", OrderStatus.PENDING, 200.00)
            );
            when(orderRepository.findByCustomerIdOrderByCreatedAtDesc("customer-B"))
                    .thenReturn(orders);

            // When: we list orders for customer-B.
            List<Order> result = orderService.findByCustomerId("customer-B");

            // Then: both orders are returned.
            assertThat(result).hasSize(2);
            assertThat(result).extracting(Order::getCustomerId)
                    .containsOnly("customer-B");
        }

        @Test
        @DisplayName("returns empty list when customer has no orders")
        void returnsEmptyListForNewCustomer() {
            // Given: no orders for customer-C.
            when(orderRepository.findByCustomerIdOrderByCreatedAtDesc("customer-C"))
                    .thenReturn(List.of());

            // When: we list orders.
            List<Order> result = orderService.findByCustomerId("customer-C");

            // Then: empty list.
            assertThat(result).isEmpty();
        }
    }

    // =========================================================================
    // Tests for createOrder()
    // =========================================================================

    @Nested
    @DisplayName("createOrder()")
    class CreateOrder {

        @Test
        @DisplayName("creates order with PENDING status and correct total")
        void createsOrderWithPendingStatusAndCorrectTotal() {
            // Given: the repository saves and returns the order.
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

            // Two line items: 1 × $999.99 + 2 × $29.99 = $1059.97
            List<OrderLineItem> items = List.of(
                    new OrderLineItem("SKU-A", "Laptop", 1, 999.99),
                    new OrderLineItem("SKU-B", "Mouse", 2, 29.99)
            );

            // When: we create the order.
            Order result = orderService.createOrder("customer-X", items);

            // Then: order has PENDING status.
            assertThat(result.getStatus()).isEqualTo(OrderStatus.PENDING);
            assertThat(result.getCustomerId()).isEqualTo("customer-X");

            // And: total is sum of (qty * unitPrice) for all items.
            double expectedTotal = 1 * 999.99 + 2 * 29.99;
            assertThat(result.getTotalAmount()).isEqualTo(expectedTotal, org.assertj.core.api.Assertions.within(0.001));

            // And: a UUID was assigned as the order ID (not null or blank).
            assertThat(result.getOrderId()).isNotBlank();
            assertThat(result.getOrderId()).matches(
                    "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");
        }

        @Test
        @DisplayName("persists each line item with back-reference to the order")
        void persistsLineItemsWithBackReference() {
            // Given: the repository saves and returns the order.
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

            List<OrderLineItem> items = List.of(
                    new OrderLineItem("SKU-C", "Keyboard", 3, 49.99)
            );

            // When: we create the order.
            Order result = orderService.createOrder("customer-Y", items);

            // Then: the order has the correct number of items.
            assertThat(result.getItems()).hasSize(1);
            assertThat(result.getItems().get(0).getSku()).isEqualTo("SKU-C");
            // And: each item's back-reference points to this order.
            assertThat(result.getItems().get(0).getOrder()).isSameAs(result);
        }

        @Test
        @DisplayName("throws IllegalArgumentException for blank customerId")
        void throwsForBlankCustomerId() {
            // When + Then: blank customer ID throws immediately.
            assertThatThrownBy(() ->
                    orderService.createOrder("  ", List.of(new OrderLineItem("SKU-A", "X", 1, 9.99))))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Customer ID");

            // Repository should never be called.
            verify(orderRepository, never()).save(any());
        }

        @Test
        @DisplayName("throws IllegalArgumentException for empty item list")
        void throwsForEmptyItemList() {
            // When + Then: no items → exception.
            assertThatThrownBy(() ->
                    orderService.createOrder("customer-Z", List.of()))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("at least one item");

            verify(orderRepository, never()).save(any());
        }
    }

    // =========================================================================
    // Tests for updateStatus()
    // =========================================================================

    @Nested
    @DisplayName("updateStatus()")
    class UpdateStatus {

        @Test
        @DisplayName("updates status and returns the updated order")
        void updatesStatusSuccessfully() {
            // Given: an existing PENDING order.
            Order order = buildOrder("order-200", "customer-A", OrderStatus.PENDING, 100.00);
            when(orderRepository.findById("order-200")).thenReturn(Optional.of(order));
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

            // When: we update the status to CONFIRMED.
            Optional<Order> result = orderService.updateStatus("order-200", OrderStatus.CONFIRMED);

            // Then: the order status is now CONFIRMED.
            assertThat(result).isPresent();
            assertThat(result.get().getStatus()).isEqualTo(OrderStatus.CONFIRMED);
        }

        @Test
        @DisplayName("returns empty Optional when order does not exist")
        void returnsEmptyWhenOrderNotFound() {
            // Given: no order with this ID.
            when(orderRepository.findById("no-order")).thenReturn(Optional.empty());

            // When: we try to update a non-existent order.
            Optional<Order> result = orderService.updateStatus("no-order", OrderStatus.CANCELLED);

            // Then: empty Optional (no exception thrown).
            assertThat(result).isEmpty();
        }

        @Test
        @DisplayName("captures the exact new status that is persisted")
        void capturesPersistedStatus() {
            // Given: an existing CONFIRMED order.
            Order order = buildOrder("order-300", "customer-B", OrderStatus.CONFIRMED, 250.00);
            when(orderRepository.findById("order-300")).thenReturn(Optional.of(order));
            when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

            // When: we update to SHIPPED.
            orderService.updateStatus("order-300", OrderStatus.SHIPPED);

            // Then: verify the argument passed to save() had status SHIPPED.
            ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
            verify(orderRepository).save(captor.capture());
            assertThat(captor.getValue().getStatus()).isEqualTo(OrderStatus.SHIPPED);
        }
    }

    // =========================================================================
    // Helper methods
    // =========================================================================

    /**
     * Build an {@link Order} instance for use in test setups.
     * The Order ID is set via the constructor (not auto-generated here).
     */
    private Order buildOrder(String orderId, String customerId,
                              OrderStatus status, double totalAmount) {
        return new Order(orderId, customerId, status, totalAmount, LocalDateTime.now());
    }
}
