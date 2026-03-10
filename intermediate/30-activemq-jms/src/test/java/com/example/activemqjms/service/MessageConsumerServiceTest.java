package com.example.activemqjms.service;

import com.example.activemqjms.domain.OrderMessage;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link MessageConsumerService}.
 *
 * <p>These tests verify that the consumer correctly delegates received messages
 * to the domain processing layer — without starting any JMS infrastructure,
 * Spring context, or ActiveMQ broker.
 *
 * <h2>Testing strategy</h2>
 * <ul>
 *   <li>{@link Mock} — creates a Mockito mock of {@link OrderProcessingService}.
 *       We can then verify that the consumer calls it with the correct argument.</li>
 *   <li>{@link InjectMocks} — creates a real {@link MessageConsumerService} and
 *       injects the mock {@link OrderProcessingService} via constructor injection.</li>
 * </ul>
 *
 * <h2>What we do NOT test here</h2>
 * <p>The {@link org.springframework.jms.annotation.JmsListener} annotation binding
 * (which queue the listener attaches to, deserialisation) is Spring infrastructure
 * and is tested in the integration test ({@code ActiveMqJmsIntegrationTest}).
 * Unit tests focus solely on the Java method logic.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MessageConsumerService unit tests")
class MessageConsumerServiceTest {

    /**
     * Mocked {@link OrderProcessingService} — records calls but performs no real processing.
     */
    @Mock
    private OrderProcessingService orderProcessingService;

    /**
     * The class under test.
     * {@code @InjectMocks} creates a real {@link MessageConsumerService} and injects
     * the mocked {@link OrderProcessingService} via constructor.
     */
    @InjectMocks
    private MessageConsumerService messageConsumerService;

    // ── receiveOrder ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("receiveOrder delegates to OrderProcessingService.processOrder exactly once")
    void receiveOrder_delegatesToOrderProcessingService() {
        // Given: an order message (as if deserialised from the JMS queue)
        OrderMessage message = new OrderMessage("ORD-001", "Laptop", 2);

        // When: the consumer receives the message
        messageConsumerService.receiveOrder(message);

        // Then: processOrder must have been called exactly once with the same message
        verify(orderProcessingService, times(1)).processOrder(message);
    }

    @Test
    @DisplayName("receiveOrder passes the exact message object to OrderProcessingService")
    void receiveOrder_passesExactMessageToProcessingService() {
        // Given: a message with specific field values
        OrderMessage message = new OrderMessage("ORD-EXACT", "Camera", 3);

        // When: the consumer receives the message
        messageConsumerService.receiveOrder(message);

        // Then: the same object reference is passed to the processing service
        // (no copying or wrapping occurs inside the consumer)
        verify(orderProcessingService).processOrder(message);
    }
}
