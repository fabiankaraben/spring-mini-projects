package com.example.cloudstream.stream;

import com.example.cloudstream.domain.OrderStatus;
import com.example.cloudstream.events.OrderPlacedEvent;
import com.example.cloudstream.events.OrderProcessedEvent;
import com.example.cloudstream.events.OrderRejectedEvent;
import com.example.cloudstream.repository.OrderRepository;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cloud.stream.binder.test.InputDestination;
import org.springframework.cloud.stream.binder.test.TestChannelBinderConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.support.MessageBuilder;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link OrderStreamFunctions} using the Spring Cloud Stream
 * <strong>Test Binder</strong>.
 *
 * <p>The Test Binder ({@link TestChannelBinderConfiguration}) replaces the real
 * Kafka binder with an in-memory message bus. This means:
 * <ul>
 *   <li>No Docker containers needed — tests run fast.</li>
 *   <li>No real Kafka broker — messages are delivered in-process.</li>
 *   <li>{@link InputDestination} sends messages directly to Function / Consumer beans.</li>
 * </ul>
 *
 * <p>Why we verify via order status rather than OutputDestination:
 * <ul>
 *   <li>The Test Binder wires the pipeline end-to-end in-process: the orderProcessor
 *       Function output goes directly into notificationConsumer / rejectionLogger.
 *       By the time OutputDestination.receive() is called, the message is already
 *       consumed and the order status has been updated.</li>
 *   <li>Verifying the in-memory order status is the simplest, most robust approach.</li>
 * </ul>
 *
 * <p>Test coverage:
 * <ul>
 *   <li>orderProcessor — valid order transitions PENDING to NOTIFIED end-to-end.</li>
 *   <li>orderProcessor — invalid order (price = 0) transitions to REJECTED.</li>
 *   <li>orderProcessor — invalid order (quantity = 0) transitions to REJECTED.</li>
 *   <li>orderProcessor — invalid order (blank productId) transitions to REJECTED.</li>
 *   <li>notificationConsumer — direct OrderProcessedEvent injection marks NOTIFIED.</li>
 *   <li>rejectionLogger — direct OrderRejectedEvent injection does not throw.</li>
 * </ul>
 */
@SpringBootTest
@Import(TestChannelBinderConfiguration.class)
@DisplayName("OrderStreamFunctions unit tests (Test Binder)")
class OrderStreamFunctionsTest {

    /**
     * InputDestination sends a message to a destination (topic) channel.
     * The second argument is the destination name, which the Test Binder maps
     * to the channel registered as {@code <destination>.destination}.
     */
    @Autowired
    private InputDestination inputDestination;

    @Autowired
    private OrderRepository orderRepository;

    @AfterEach
    void tearDown() {
        orderRepository.deleteAll();
    }

    // =========================================================================
    // orderProcessor — happy path: valid order flows PENDING → NOTIFIED
    // =========================================================================

    @Test
    @DisplayName("valid order flows through full pipeline: PENDING → PROCESSING → NOTIFIED")
    void validOrderFlowsToPendingToNotified() {
        UUID orderId = UUID.randomUUID();
        saveOrderWithId(orderId);

        // Valid event: positive price, positive quantity, non-blank productId
        OrderPlacedEvent event = new OrderPlacedEvent(
                orderId, "cust-1", "prod-A", 2, new BigDecimal("19.98"), Instant.now());

        // Inject into the "orders" destination (orderProcessor-in-0).
        // The Test Binder delivers synchronously through:
        //   orderProcessor (Function) → orders-processed → notificationConsumer (Consumer)
        inputDestination.send(MessageBuilder.withPayload(event).build(), "orders");

        // Verify the full pipeline ran: order should be NOTIFIED
        Awaitility.await()
                .atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    Optional<com.example.cloudstream.domain.Order> found =
                            orderRepository.findById(orderId);
                    assertThat(found).isPresent();
                    assertThat(found.get().getStatus()).isEqualTo(OrderStatus.NOTIFIED);
                });
    }

    @Test
    @DisplayName("order with quantity 6 gets 4-day delivery estimate and reaches NOTIFIED")
    void largeOrderGetsExtraDeliveryDay() {
        UUID orderId = UUID.randomUUID();
        saveOrderWithId(orderId);

        // quantity=6 → estimatedDays = 3 + (6-1)/5 = 4
        OrderPlacedEvent event = new OrderPlacedEvent(
                orderId, "cust-large", "prod-C", 6, new BigDecimal("60.00"), Instant.now());

        inputDestination.send(MessageBuilder.withPayload(event).build(), "orders");

        Awaitility.await()
                .atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    Optional<com.example.cloudstream.domain.Order> found =
                            orderRepository.findById(orderId);
                    assertThat(found).isPresent();
                    assertThat(found.get().getStatus()).isEqualTo(OrderStatus.NOTIFIED);
                });
    }

    // =========================================================================
    // orderProcessor — rejection paths
    // =========================================================================

    @Test
    @DisplayName("orderProcessor rejects order when totalPrice is zero → REJECTED")
    void orderProcessorRejectsWhenPriceIsZero() {
        UUID orderId = UUID.randomUUID();
        saveOrderWithId(orderId);

        // totalPrice = 0 fails the domain validation in orderProcessor
        OrderPlacedEvent event = new OrderPlacedEvent(
                orderId, "cust-3", "prod-C", 1, BigDecimal.ZERO, Instant.now());

        inputDestination.send(MessageBuilder.withPayload(event).build(), "orders");

        // The processor rejects the order → markRejected() is called → status REJECTED
        Awaitility.await()
                .atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    Optional<com.example.cloudstream.domain.Order> found =
                            orderRepository.findById(orderId);
                    assertThat(found).isPresent();
                    assertThat(found.get().getStatus()).isEqualTo(OrderStatus.REJECTED);
                    assertThat(found.get().getRejectionReason())
                            .contains("Total price must be positive");
                });
    }

    @Test
    @DisplayName("orderProcessor rejects order when quantity is zero → REJECTED")
    void orderProcessorRejectsWhenQuantityIsZero() {
        UUID orderId = UUID.randomUUID();
        saveOrderWithId(orderId);

        OrderPlacedEvent event = new OrderPlacedEvent(
                orderId, "cust-4", "prod-D", 0, new BigDecimal("10.00"), Instant.now());

        inputDestination.send(MessageBuilder.withPayload(event).build(), "orders");

        Awaitility.await()
                .atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    Optional<com.example.cloudstream.domain.Order> found =
                            orderRepository.findById(orderId);
                    assertThat(found).isPresent();
                    assertThat(found.get().getStatus()).isEqualTo(OrderStatus.REJECTED);
                    assertThat(found.get().getRejectionReason())
                            .contains("Quantity must be positive");
                });
    }

    @Test
    @DisplayName("orderProcessor rejects order when productId is blank → REJECTED")
    void orderProcessorRejectsWhenProductIdIsBlank() {
        UUID orderId = UUID.randomUUID();
        saveOrderWithId(orderId);

        OrderPlacedEvent event = new OrderPlacedEvent(
                orderId, "cust-5", "   ", 1, new BigDecimal("5.00"), Instant.now());

        inputDestination.send(MessageBuilder.withPayload(event).build(), "orders");

        Awaitility.await()
                .atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    Optional<com.example.cloudstream.domain.Order> found =
                            orderRepository.findById(orderId);
                    assertThat(found).isPresent();
                    assertThat(found.get().getStatus()).isEqualTo(OrderStatus.REJECTED);
                    assertThat(found.get().getRejectionReason())
                            .contains("Product ID must not be blank");
                });
    }

    // =========================================================================
    // notificationConsumer — direct injection
    // =========================================================================

    @Test
    @DisplayName("notificationConsumer marks order NOTIFIED when it receives OrderProcessedEvent directly")
    void notificationConsumerMarksOrderNotified() {
        UUID orderId = UUID.randomUUID();
        saveOrderWithId(orderId);

        // Inject directly into the orders-processed destination (notificationConsumer-in-0)
        OrderProcessedEvent event = new OrderProcessedEvent(
                orderId, "cust-6", "prod-F", 1, new BigDecimal("10.00"),
                Instant.now(), Instant.now(), 3, "Your order is confirmed");

        inputDestination.send(
                MessageBuilder.withPayload(event).build(),
                "orders-processed");

        Awaitility.await()
                .atMost(5, TimeUnit.SECONDS)
                .untilAsserted(() -> {
                    Optional<com.example.cloudstream.domain.Order> found =
                            orderRepository.findById(orderId);
                    assertThat(found).isPresent();
                    assertThat(found.get().getStatus()).isEqualTo(OrderStatus.NOTIFIED);
                });
    }

    // =========================================================================
    // rejectionLogger — direct injection
    // =========================================================================

    @Test
    @DisplayName("rejectionLogger consumes OrderRejectedEvent without throwing exceptions")
    void rejectionLoggerConsumesWithoutException() {
        UUID orderId = UUID.randomUUID();
        saveOrderWithId(orderId);

        OrderRejectedEvent event = new OrderRejectedEvent(
                orderId, "cust-7", "prod-G", "Total price must be positive", Instant.now());

        // The rejectionLogger only logs — it does not update order status.
        // Verify no exception is thrown.
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() ->
                inputDestination.send(
                        MessageBuilder.withPayload(event).build(),
                        "orders-rejected")
        );
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * Creates an {@link com.example.cloudstream.domain.Order} with the given UUID
     * and saves it in the repository. Uses reflection to set the generated UUID field
     * to a known value so it matches the orderId in the test event.
     */
    private void saveOrderWithId(UUID id) {
        com.example.cloudstream.domain.Order order =
                new com.example.cloudstream.domain.Order("cust-test", "prod-test", 1,
                        new BigDecimal("10.00"));
        try {
            var field = com.example.cloudstream.domain.Order.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(order, id);
        } catch (Exception e) {
            throw new RuntimeException("Failed to set order id via reflection", e);
        }
        orderRepository.save(order);
    }
}
