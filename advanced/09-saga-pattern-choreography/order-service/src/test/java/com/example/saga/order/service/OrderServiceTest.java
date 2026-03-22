package com.example.saga.order.service;

import com.example.saga.order.domain.Order;
import com.example.saga.order.domain.OrderStatus;
import com.example.saga.order.events.*;
import com.example.saga.order.repository.OrderRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link OrderService}.
 *
 * <p>This class tests all the saga logic in the Order Service in complete
 * isolation — no Spring context, no database, no Kafka broker. All
 * dependencies ({@link OrderRepository}, {@link KafkaTemplate}) are replaced
 * with Mockito mocks.
 *
 * <p>Mockito is configured via the {@link ExtendWith(MockitoExtension.class)}
 * annotation which processes {@code @Mock} fields automatically.
 *
 * <p>Test strategy:
 * <ul>
 *   <li>Happy path — order creation, payment processed, inventory reserved → COMPLETED.</li>
 *   <li>Payment failure path — PaymentFailedEvent → CANCELLED, no refund published.</li>
 *   <li>Inventory failure path — InventoryFailedEvent → PaymentRefundEvent published + CANCELLED.</li>
 *   <li>Idempotency guards — events arriving in wrong state are ignored.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("OrderService unit tests")
class OrderServiceTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private KafkaTemplate<String, Object> kafkaTemplate;

    private OrderService orderService;

    @BeforeEach
    void setUp() {
        orderService = new OrderService(orderRepository, kafkaTemplate);
    }

    // =========================================================================
    // createOrder
    // =========================================================================

    @Test
    @DisplayName("createOrder persists order, transitions to PAYMENT_PROCESSING, and publishes OrderCreatedEvent")
    void createOrderPublishesEvent() {
        // Arrange: repository.save() returns the same order passed in (simulates JPA behavior)
        when(orderRepository.save(any(Order.class))).thenAnswer(inv -> inv.getArgument(0));

        // Act
        Order result = orderService.createOrder("cust-1", "prod-A", 2, new BigDecimal("29.98"));

        // Assert: order saved at least twice (once PENDING, once PAYMENT_PROCESSING)
        verify(orderRepository, atLeast(2)).save(any(Order.class));

        // Assert: status is PAYMENT_PROCESSING after publish
        assertThat(result.getStatus()).isEqualTo(OrderStatus.PAYMENT_PROCESSING);

        // Assert: OrderCreatedEvent published to the correct topic
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(eq(OrderService.TOPIC_ORDER_CREATED), anyString(), eventCaptor.capture());
        assertThat(eventCaptor.getValue()).isInstanceOf(OrderCreatedEvent.class);

        OrderCreatedEvent published = (OrderCreatedEvent) eventCaptor.getValue();
        assertThat(published.customerId()).isEqualTo("cust-1");
        assertThat(published.productId()).isEqualTo("prod-A");
        assertThat(published.quantity()).isEqualTo(2);
        assertThat(published.totalPrice()).isEqualByComparingTo("29.98");
    }

    // =========================================================================
    // handlePaymentProcessed
    // =========================================================================

    @Test
    @DisplayName("handlePaymentProcessed transitions order to INVENTORY_RESERVING")
    void handlePaymentProcessedTransitionsStatus() {
        // Arrange
        Order order = buildOrder(OrderStatus.PAYMENT_PROCESSING);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PaymentProcessedEvent event = new PaymentProcessedEvent(
                order.getId(), "cust-1", UUID.randomUUID(), new BigDecimal("29.98"));

        // Act
        orderService.handlePaymentProcessed(event);

        // Assert
        assertThat(order.getStatus()).isEqualTo(OrderStatus.INVENTORY_RESERVING);
        verify(orderRepository).save(order);
    }

    @Test
    @DisplayName("handlePaymentProcessed ignores event if order is not in PAYMENT_PROCESSING status")
    void handlePaymentProcessedIgnoresWrongStatus() {
        Order order = buildOrder(OrderStatus.COMPLETED);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        PaymentProcessedEvent event = new PaymentProcessedEvent(
                order.getId(), "cust-1", UUID.randomUUID(), new BigDecimal("29.98"));

        orderService.handlePaymentProcessed(event);

        // No save should happen — status guard prevented processing
        verify(orderRepository, never()).save(any());
        assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
    }

    // =========================================================================
    // handlePaymentFailed
    // =========================================================================

    @Test
    @DisplayName("handlePaymentFailed transitions order to CANCELLED with failure reason")
    void handlePaymentFailedCancelsOrder() {
        Order order = buildOrder(OrderStatus.PAYMENT_PROCESSING);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        PaymentFailedEvent event = new PaymentFailedEvent(
                order.getId(), "cust-1", "Insufficient funds");

        orderService.handlePaymentFailed(event);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getFailureReason()).contains("Insufficient funds");
        // No refund event — payment was never charged
        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
    }

    // =========================================================================
    // handleInventoryReserved
    // =========================================================================

    @Test
    @DisplayName("handleInventoryReserved transitions order to COMPLETED")
    void handleInventoryReservedCompletesOrder() {
        Order order = buildOrder(OrderStatus.INVENTORY_RESERVING);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        InventoryReservedEvent event = new InventoryReservedEvent(
                order.getId(), "prod-A", 2, UUID.randomUUID());

        orderService.handleInventoryReserved(event);

        assertThat(order.getStatus()).isEqualTo(OrderStatus.COMPLETED);
        assertThat(order.getFailureReason()).isNull();
        verify(orderRepository).save(order);
    }

    // =========================================================================
    // handleInventoryFailed
    // =========================================================================

    @Test
    @DisplayName("handleInventoryFailed publishes PaymentRefundEvent and cancels order")
    void handleInventoryFailedPublishesRefundAndCancels() {
        // Arrange: order in INVENTORY_RESERVING with stored payment context
        Order order = buildOrder(OrderStatus.INVENTORY_RESERVING);
        UUID paymentId = UUID.randomUUID();
        BigDecimal amount = new BigDecimal("29.98");

        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        // Simulate prior payment processing that stored context
        PaymentProcessedEvent paymentEvent = new PaymentProcessedEvent(
                order.getId(), "cust-1", paymentId, amount);
        // Manually prime the internal maps by first calling handlePaymentProcessed
        // on a copy in PAYMENT_PROCESSING state, then switch to INVENTORY_RESERVING
        Order paymentOrder = buildOrderWithId(order.getId(), OrderStatus.PAYMENT_PROCESSING);
        when(orderRepository.findById(order.getId()))
                .thenReturn(Optional.of(paymentOrder))   // first call for handlePaymentProcessed
                .thenReturn(Optional.of(order));          // second call for handleInventoryFailed
        when(orderRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        orderService.handlePaymentProcessed(paymentEvent);

        InventoryFailedEvent failEvent = new InventoryFailedEvent(
                order.getId(), "prod-A", "Out of stock");

        // Act
        orderService.handleInventoryFailed(failEvent);

        // Assert: refund event published
        ArgumentCaptor<Object> eventCaptor = ArgumentCaptor.forClass(Object.class);
        verify(kafkaTemplate).send(eq(OrderService.TOPIC_PAYMENT_REFUND), anyString(), eventCaptor.capture());
        assertThat(eventCaptor.getValue()).isInstanceOf(PaymentRefundEvent.class);

        PaymentRefundEvent refund = (PaymentRefundEvent) eventCaptor.getValue();
        assertThat(refund.paymentId()).isEqualTo(paymentId);
        assertThat(refund.amountToRefund()).isEqualByComparingTo(amount);
        assertThat(refund.reason()).contains("Out of stock");

        // Assert: order cancelled
        assertThat(order.getStatus()).isEqualTo(OrderStatus.CANCELLED);
        assertThat(order.getFailureReason()).contains("Out of stock");
    }

    @Test
    @DisplayName("handleInventoryFailed ignores event if order is not in INVENTORY_RESERVING status")
    void handleInventoryFailedIgnoresWrongStatus() {
        Order order = buildOrder(OrderStatus.COMPLETED);
        when(orderRepository.findById(order.getId())).thenReturn(Optional.of(order));

        InventoryFailedEvent event = new InventoryFailedEvent(order.getId(), "prod-A", "Out of stock");

        orderService.handleInventoryFailed(event);

        verify(kafkaTemplate, never()).send(anyString(), anyString(), any());
        verify(orderRepository, never()).save(any());
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * Builds an {@link Order} with a fixed UUID in the given status,
     * using reflection to set the normally JPA-managed id field.
     */
    private Order buildOrder(OrderStatus status) {
        return buildOrderWithId(UUID.randomUUID(), status);
    }

    private Order buildOrderWithId(UUID id, OrderStatus status) {
        Order order = new Order("cust-1", "prod-A", 2, new BigDecimal("29.98"));
        // Use reflection to set the @GeneratedValue id field
        try {
            var field = Order.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(order, id);
            // Also set status via the transition methods
            if (status == OrderStatus.PAYMENT_PROCESSING) {
                order.markPaymentProcessing();
            } else if (status == OrderStatus.INVENTORY_RESERVING) {
                order.markPaymentProcessing();
                order.markInventoryReserving();
            } else if (status == OrderStatus.COMPLETED) {
                order.markPaymentProcessing();
                order.markInventoryReserving();
                order.markCompleted();
            } else if (status == OrderStatus.CANCELLED) {
                order.markPaymentProcessing();
                order.markCancelled("test cancellation");
            }
        } catch (Exception e) {
            throw new RuntimeException("Failed to set order fields via reflection", e);
        }
        return order;
    }
}
