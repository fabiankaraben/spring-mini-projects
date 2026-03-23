package com.example.cloudstream.service;

import com.example.cloudstream.domain.Order;
import com.example.cloudstream.domain.OrderStatus;
import com.example.cloudstream.events.OrderPlacedEvent;
import com.example.cloudstream.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link OrderService}.
 *
 * <p>All dependencies are replaced with Mockito mocks. No Spring context,
 * no Kafka, no database — pure unit test speed.
 *
 * <p>{@link ExtendWith(MockitoExtension.class)} processes {@code @Mock} fields
 * automatically via the Mockito JUnit 5 extension.
 *
 * <p>Coverage:
 * <ul>
 *   <li>{@link OrderService#placeOrder} — saves order and enqueues event.</li>
 *   <li>{@link OrderService#pollNextEvent} — drains the queue correctly.</li>
 *   <li>{@link OrderService#markProcessing} — updates order status.</li>
 *   <li>{@link OrderService#markNotified} — updates order status.</li>
 *   <li>{@link OrderService#markRejected} — updates order status with reason.</li>
 *   <li>{@link OrderService#findById} — delegates to repository.</li>
 *   <li>Missing-order guard — warn-and-skip when order not found.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrderService unit tests")
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        // Construct service with the mocked repository
        orderService = new OrderService(orderRepository);
    }

    // =========================================================================
    // placeOrder
    // =========================================================================

    @Test
    @DisplayName("placeOrder saves the order and returns it")
    void placeOrderSavesOrder() {
        // Arrange: make save() return the same object passed in (mirrors JPA behavior)
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        Order result = orderService.placeOrder("cust-1", "prod-A", 2, new BigDecimal("19.98"));

        // Assert: save was called once
        ArgumentCaptor<Order> captor = ArgumentCaptor.forClass(Order.class);
        verify(orderRepository).save(captor.capture());

        Order saved = captor.getValue();
        assertThat(saved.getCustomerId()).isEqualTo("cust-1");
        assertThat(saved.getProductId()).isEqualTo("prod-A");
        assertThat(saved.getQuantity()).isEqualTo(2);
        assertThat(saved.getTotalPrice()).isEqualByComparingTo("19.98");
        assertThat(saved.getStatus()).isEqualTo(OrderStatus.PENDING);

        // Returned order is the same object
        assertThat(result).isEqualTo(saved);
    }

    @Test
    @DisplayName("placeOrder enqueues exactly one OrderPlacedEvent with matching fields")
    void placeOrderEnqueuesEvent() {
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        orderService.placeOrder("cust-2", "prod-B", 3, new BigDecimal("29.97"));

        // Poll the event that was enqueued
        OrderPlacedEvent event = orderService.pollNextEvent();

        assertThat(event).isNotNull();
        assertThat(event.customerId()).isEqualTo("cust-2");
        assertThat(event.productId()).isEqualTo("prod-B");
        assertThat(event.quantity()).isEqualTo(3);
        assertThat(event.totalPrice()).isEqualByComparingTo("29.97");
        assertThat(event.orderId()).isNotNull();
    }

    // =========================================================================
    // pollNextEvent
    // =========================================================================

    @Test
    @DisplayName("pollNextEvent returns null when the queue is empty")
    void pollNextEventReturnsNullWhenEmpty() {
        // No placeOrder call — queue should be empty
        assertThat(orderService.pollNextEvent()).isNull();
    }

    @Test
    @DisplayName("pollNextEvent drains events in FIFO order")
    void pollNextEventDrainsInFifoOrder() {
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        // Enqueue two events
        orderService.placeOrder("cust-A", "prod-1", 1, new BigDecimal("10.00"));
        orderService.placeOrder("cust-B", "prod-2", 2, new BigDecimal("20.00"));

        // First poll should return the first event (FIFO)
        OrderPlacedEvent first = orderService.pollNextEvent();
        assertThat(first.customerId()).isEqualTo("cust-A");

        // Second poll returns the second event
        OrderPlacedEvent second = orderService.pollNextEvent();
        assertThat(second.customerId()).isEqualTo("cust-B");

        // Queue is now empty
        assertThat(orderService.pollNextEvent()).isNull();
    }

    // =========================================================================
    // markProcessing
    // =========================================================================

    @Test
    @DisplayName("markProcessing updates order status to PROCESSING in repository")
    void markProcessingUpdatesStatus() {
        Order order = new Order("cust-3", "prod-C", 1, new BigDecimal("9.99"));
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        orderService.markProcessing(order.getId());

        assertThat(order.getStatus()).isEqualTo(OrderStatus.PROCESSING);
        verify(orderRepository).save(order);
    }

    @Test
    @DisplayName("markProcessing does nothing if order not found")
    void markProcessingDoesNothingIfNotFound() {
        UUID unknownId = UUID.randomUUID();
        when(orderRepository.findById(unknownId)).thenReturn(Optional.empty());

        // Should not throw, should not call save
        orderService.markProcessing(unknownId);

        verify(orderRepository, never()).save(any());
    }

    // =========================================================================
    // markNotified
    // =========================================================================

    @Test
    @DisplayName("markNotified updates order status to NOTIFIED in repository")
    void markNotifiedUpdatesStatus() {
        Order order = new Order("cust-4", "prod-D", 1, new BigDecimal("5.00"));
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        orderService.markNotified(order.getId());

        assertThat(order.getStatus()).isEqualTo(OrderStatus.NOTIFIED);
        verify(orderRepository).save(order);
    }

    @Test
    @DisplayName("markNotified does nothing if order not found")
    void markNotifiedDoesNothingIfNotFound() {
        UUID unknownId = UUID.randomUUID();
        when(orderRepository.findById(unknownId)).thenReturn(Optional.empty());

        orderService.markNotified(unknownId);

        verify(orderRepository, never()).save(any());
    }

    // =========================================================================
    // markRejected
    // =========================================================================

    @Test
    @DisplayName("markRejected updates order status to REJECTED with reason in repository")
    void markRejectedUpdatesStatusAndReason() {
        Order order = new Order("cust-5", "prod-E", 1, new BigDecimal("0.00"));
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        orderService.markRejected(order.getId(), "Total price must be positive");

        assertThat(order.getStatus()).isEqualTo(OrderStatus.REJECTED);
        assertThat(order.getRejectionReason()).isEqualTo("Total price must be positive");
        verify(orderRepository).save(order);
    }

    @Test
    @DisplayName("markRejected does nothing if order not found")
    void markRejectedDoesNothingIfNotFound() {
        UUID unknownId = UUID.randomUUID();
        when(orderRepository.findById(unknownId)).thenReturn(Optional.empty());

        orderService.markRejected(unknownId, "some reason");

        verify(orderRepository, never()).save(any());
    }

    // =========================================================================
    // findById
    // =========================================================================

    @Test
    @DisplayName("findById delegates to the repository and returns the result")
    void findByIdDelegatesToRepository() {
        Order order = new Order("cust-6", "prod-F", 2, new BigDecimal("15.00"));
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        Optional<Order> result = orderService.findById(order.getId());

        assertThat(result).isPresent();
        assertThat(result.get().getCustomerId()).isEqualTo("cust-6");
    }

    @Test
    @DisplayName("findById returns empty Optional when order does not exist")
    void findByIdReturnsEmptyIfNotFound() {
        UUID unknownId = UUID.randomUUID();
        when(orderRepository.findById(unknownId)).thenReturn(Optional.empty());

        Optional<Order> result = orderService.findById(unknownId);

        assertThat(result).isEmpty();
    }
}
