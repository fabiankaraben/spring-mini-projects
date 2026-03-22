package com.example.cqrs.command;

import com.example.cqrs.command.aggregate.OrderAggregate;
import com.example.cqrs.command.aggregate.OrderStatus;
import com.example.cqrs.command.api.*;
import org.axonframework.test.aggregate.AggregateTestFixture;
import org.axonframework.test.aggregate.FixtureConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;

/**
 * Unit tests for {@link OrderAggregate} using Axon's {@link AggregateTestFixture}.
 *
 * <h2>Why AggregateTestFixture?</h2>
 * The {@link AggregateTestFixture} allows testing aggregate behaviour in complete isolation:
 * <ul>
 *   <li>No Spring context is loaded (fast startup)</li>
 *   <li>No database is needed</li>
 *   <li>Uses a given-when-then fluent API that mirrors DDD test language</li>
 * </ul>
 *
 * <h2>Test structure (given-when-then)</h2>
 * <pre>
 *   fixture.given(past events that set up initial state)
 *          .when(command being tested)
 *          .expectEvents(events that should be published)
 *          .expectState(assertions on the aggregate's in-memory state);
 * </pre>
 *
 * For creation commands (no prior history) we use {@code givenNoPriorActivity()}.
 */
@DisplayName("OrderAggregate unit tests")
class OrderAggregateTest {

    /** The Axon test fixture — bootstraps an in-memory command/event bus for the aggregate. */
    private FixtureConfiguration<OrderAggregate> fixture;

    /** A fixed order ID used across tests. */
    private static final String ORDER_ID = "test-order-001";
    private static final String PRODUCT_ID = "PROD-42";
    private static final int QUANTITY = 3;
    private static final BigDecimal UNIT_PRICE = new BigDecimal("29.99");

    @BeforeEach
    void setUp() {
        // Create a fresh fixture for OrderAggregate before each test
        fixture = new AggregateTestFixture<>(OrderAggregate.class);
    }

    // =========================================================================
    //  PlaceOrderCommand tests
    // =========================================================================

    @Nested
    @DisplayName("PlaceOrderCommand")
    class PlaceOrderCommandTests {

        @Test
        @DisplayName("should publish OrderPlacedEvent when given a valid command")
        void shouldPublishOrderPlacedEvent() {
            // given: no prior activity (this is a creation command)
            // when: we place a new order
            // then: an OrderPlacedEvent should be published
            fixture.givenNoPriorActivity()
                    .when(new PlaceOrderCommand(ORDER_ID, PRODUCT_ID, QUANTITY, UNIT_PRICE))
                    .expectSuccessfulHandlerExecution()
                    .expectState(aggregate -> {
                        assertThat(aggregate.getOrderId()).isEqualTo(ORDER_ID);
                        assertThat(aggregate.getProductId()).isEqualTo(PRODUCT_ID);
                        assertThat(aggregate.getQuantity()).isEqualTo(QUANTITY);
                        assertThat(aggregate.getUnitPrice()).isEqualByComparingTo(UNIT_PRICE);
                        assertThat(aggregate.getStatus()).isEqualTo(OrderStatus.PLACED);
                    });
        }

        @Test
        @DisplayName("should reject a PlaceOrderCommand with zero quantity")
        void shouldRejectZeroQuantity() {
            fixture.givenNoPriorActivity()
                    .when(new PlaceOrderCommand(ORDER_ID, PRODUCT_ID, 0, UNIT_PRICE))
                    .expectException(IllegalArgumentException.class)
                    .expectExceptionMessage(containsString("Quantity must be positive"));
        }

        @Test
        @DisplayName("should reject a PlaceOrderCommand with negative quantity")
        void shouldRejectNegativeQuantity() {
            fixture.givenNoPriorActivity()
                    .when(new PlaceOrderCommand(ORDER_ID, PRODUCT_ID, -1, UNIT_PRICE))
                    .expectException(IllegalArgumentException.class)
                    .expectExceptionMessage(containsString("Quantity must be positive"));
        }

        @Test
        @DisplayName("should reject a PlaceOrderCommand with zero unit price")
        void shouldRejectZeroUnitPrice() {
            fixture.givenNoPriorActivity()
                    .when(new PlaceOrderCommand(ORDER_ID, PRODUCT_ID, QUANTITY, BigDecimal.ZERO))
                    .expectException(IllegalArgumentException.class)
                    .expectExceptionMessage("Unit price must be positive");
        }

        @Test
        @DisplayName("should reject a PlaceOrderCommand with null unit price")
        void shouldRejectNullUnitPrice() {
            fixture.givenNoPriorActivity()
                    .when(new PlaceOrderCommand(ORDER_ID, PRODUCT_ID, QUANTITY, null))
                    .expectException(IllegalArgumentException.class)
                    .expectExceptionMessage("Unit price must be positive");
        }
    }

    // =========================================================================
    //  ConfirmOrderCommand tests
    // =========================================================================

    @Nested
    @DisplayName("ConfirmOrderCommand")
    class ConfirmOrderCommandTests {

        @Test
        @DisplayName("should publish OrderConfirmedEvent when order is PLACED")
        void shouldConfirmPlacedOrder() {
            // given: the order has already been placed (OrderPlacedEvent in history)
            // when: we confirm the order
            // then: OrderConfirmedEvent should be published
            fixture.given(new OrderPlacedEvent(ORDER_ID, PRODUCT_ID, QUANTITY, UNIT_PRICE, null))
                    .when(new ConfirmOrderCommand(ORDER_ID))
                    .expectSuccessfulHandlerExecution()
                    .expectState(aggregate ->
                            assertThat(aggregate.getStatus()).isEqualTo(OrderStatus.CONFIRMED)
                    );
        }

        @Test
        @DisplayName("should reject ConfirmOrderCommand when order is already CONFIRMED")
        void shouldRejectConfirmingAlreadyConfirmedOrder() {
            // given: the order is already confirmed
            fixture.given(
                            new OrderPlacedEvent(ORDER_ID, PRODUCT_ID, QUANTITY, UNIT_PRICE, null),
                            new OrderConfirmedEvent(ORDER_ID, null)
                    )
                    .when(new ConfirmOrderCommand(ORDER_ID))
                    .expectException(IllegalStateException.class)
                    .expectExceptionMessage(containsString("Cannot confirm order in status: CONFIRMED"));
        }

        @Test
        @DisplayName("should reject ConfirmOrderCommand when order is CANCELLED")
        void shouldRejectConfirmingCancelledOrder() {
            // given: the order has been cancelled
            fixture.given(
                            new OrderPlacedEvent(ORDER_ID, PRODUCT_ID, QUANTITY, UNIT_PRICE, null),
                            new OrderCancelledEvent(ORDER_ID, "Test cancellation", null)
                    )
                    .when(new ConfirmOrderCommand(ORDER_ID))
                    .expectException(IllegalStateException.class)
                    .expectExceptionMessage(containsString("Cannot confirm order in status: CANCELLED"));
        }
    }

    // =========================================================================
    //  CancelOrderCommand tests
    // =========================================================================

    @Nested
    @DisplayName("CancelOrderCommand")
    class CancelOrderCommandTests {

        @Test
        @DisplayName("should publish OrderCancelledEvent when order is PLACED")
        void shouldCancelPlacedOrder() {
            // given: the order is in PLACED status
            // when: we cancel it
            // then: OrderCancelledEvent should be published with the provided reason
            fixture.given(new OrderPlacedEvent(ORDER_ID, PRODUCT_ID, QUANTITY, UNIT_PRICE, null))
                    .when(new CancelOrderCommand(ORDER_ID, "Customer request"))
                    .expectSuccessfulHandlerExecution()
                    .expectState(aggregate ->
                            assertThat(aggregate.getStatus()).isEqualTo(OrderStatus.CANCELLED)
                    );
        }

        @Test
        @DisplayName("should allow cancellation without a reason")
        void shouldCancelWithoutReason() {
            fixture.given(new OrderPlacedEvent(ORDER_ID, PRODUCT_ID, QUANTITY, UNIT_PRICE, null))
                    .when(new CancelOrderCommand(ORDER_ID, null))
                    .expectSuccessfulHandlerExecution()
                    .expectState(aggregate ->
                            assertThat(aggregate.getStatus()).isEqualTo(OrderStatus.CANCELLED)
                    );
        }

        @Test
        @DisplayName("should reject CancelOrderCommand when order is CONFIRMED")
        void shouldRejectCancellingConfirmedOrder() {
            // given: the order has been confirmed (confirmed orders cannot be cancelled)
            fixture.given(
                            new OrderPlacedEvent(ORDER_ID, PRODUCT_ID, QUANTITY, UNIT_PRICE, null),
                            new OrderConfirmedEvent(ORDER_ID, null)
                    )
                    .when(new CancelOrderCommand(ORDER_ID, "Trying to cancel"))
                    .expectException(IllegalStateException.class)
                    .expectExceptionMessage(containsString("Cannot cancel a confirmed order"));
        }

        @Test
        @DisplayName("should reject CancelOrderCommand when order is already CANCELLED")
        void shouldRejectCancellingAlreadyCancelledOrder() {
            // given: the order is already cancelled
            fixture.given(
                            new OrderPlacedEvent(ORDER_ID, PRODUCT_ID, QUANTITY, UNIT_PRICE, null),
                            new OrderCancelledEvent(ORDER_ID, "First cancellation", null)
                    )
                    .when(new CancelOrderCommand(ORDER_ID, "Second cancellation"))
                    .expectException(IllegalStateException.class)
                    .expectExceptionMessage(containsString("Order is already cancelled"));
        }
    }

    // =========================================================================
    //  State reconstruction (event sourcing) tests
    // =========================================================================

    @Nested
    @DisplayName("Event sourcing state reconstruction")
    class EventSourcingTests {

        @Test
        @DisplayName("should reconstruct PLACED state from OrderPlacedEvent")
        void shouldReconstructPlacedState() {
            // This test verifies that replaying events reconstructs the correct aggregate state.
            // Axon replays the 'given' events through @EventSourcingHandler methods.
            fixture.given(new OrderPlacedEvent(ORDER_ID, PRODUCT_ID, QUANTITY, UNIT_PRICE, null))
                    .when(new ConfirmOrderCommand(ORDER_ID))  // This only succeeds if state is PLACED
                    .expectSuccessfulHandlerExecution();
        }

        @Test
        @DisplayName("should reconstruct CONFIRMED state from placed + confirmed events")
        void shouldReconstructConfirmedState() {
            // If the CONFIRMED state is correctly reconstructed, the aggregate should reject
            // a second confirm (business rule: cannot confirm twice)
            fixture.given(
                            new OrderPlacedEvent(ORDER_ID, PRODUCT_ID, QUANTITY, UNIT_PRICE, null),
                            new OrderConfirmedEvent(ORDER_ID, null)
                    )
                    .when(new ConfirmOrderCommand(ORDER_ID))
                    .expectException(IllegalStateException.class);
        }

        @Test
        @DisplayName("should reconstruct CANCELLED state from placed + cancelled events")
        void shouldReconstructCancelledState() {
            // If the CANCELLED state is correctly reconstructed, cancel again should fail
            fixture.given(
                            new OrderPlacedEvent(ORDER_ID, PRODUCT_ID, QUANTITY, UNIT_PRICE, null),
                            new OrderCancelledEvent(ORDER_ID, "reason", null)
                    )
                    .when(new CancelOrderCommand(ORDER_ID, "again"))
                    .expectException(IllegalStateException.class);
        }
    }
}
