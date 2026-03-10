package com.example.activemqjms.service;

import com.example.activemqjms.domain.OrderMessage;
import com.example.activemqjms.dto.OrderRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jms.core.JmsTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * Unit tests for {@link MessageProducerService}.
 *
 * <p>These tests verify the producer logic in complete isolation — no Spring context,
 * no ActiveMQ broker, no network. {@link JmsTemplate} is mocked so we can assert
 * exactly which destination and payload the service passes to it.
 *
 * <h2>Testing strategy</h2>
 * <ul>
 *   <li>{@link ExtendWith}({@link MockitoExtension}.class) — enables Mockito annotation
 *       processing ({@code @Mock}, {@code @InjectMocks}) without loading a Spring context.</li>
 *   <li>{@link Mock} — creates a Mockito mock of {@link JmsTemplate}. All method calls on
 *       the mock are recorded but perform no real action (fire-and-forget).</li>
 *   <li>{@link InjectMocks} — creates a real {@link MessageProducerService} instance and
 *       injects the mocked {@link JmsTemplate} via constructor injection.</li>
 *   <li>{@link ReflectionTestUtils#setField} — bypasses Spring's {@code @Value} injection
 *       mechanism to populate private fields directly, since there is no Spring context.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MessageProducerService unit tests")
class MessageProducerServiceTest {

    /**
     * Mocked {@link JmsTemplate} — records all calls but does not connect to any broker.
     */
    @Mock
    private JmsTemplate jmsTemplate;

    /**
     * The class under test.
     * {@code @InjectMocks} creates a {@link MessageProducerService} instance and injects
     * the {@code @Mock} field (jmsTemplate) via constructor.
     */
    @InjectMocks
    private MessageProducerService messageProducerService;

    /** Test constant for the queue name that would normally come from application.yml. */
    private static final String TEST_QUEUE = "test.orders.queue";

    /**
     * Inject the {@code @Value}-annotated fields that Spring would normally populate.
     *
     * <p>{@link ReflectionTestUtils#setField} bypasses Spring's {@code @Value}
     * injection mechanism and writes directly to private fields. This is necessary
     * because we are not loading a Spring context in these unit tests.
     */
    @BeforeEach
    void setUp() {
        // Simulate Spring's @Value injection for the ordersQueue field
        ReflectionTestUtils.setField(messageProducerService, "ordersQueue", TEST_QUEUE);
    }

    // ── publishOrder ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("publishOrder calls JmsTemplate.convertAndSend with the correct destination")
    void publishOrder_callsJmsTemplateWithCorrectDestination() {
        // Given: a valid order request
        OrderRequest request = new OrderRequest("ORD-001", "Laptop", 2);

        // When: the service publishes the order
        messageProducerService.publishOrder(request);

        // Then: JmsTemplate.convertAndSend was called exactly once with our queue name
        verify(jmsTemplate, times(1))
                .convertAndSend(eq(TEST_QUEUE), any(OrderMessage.class));
    }

    @Test
    @DisplayName("publishOrder maps DTO fields correctly to the domain OrderMessage")
    void publishOrder_mapsRequestFieldsToOrderMessage() {
        // Given: a specific order request
        OrderRequest request = new OrderRequest("ORD-42", "Wireless Mouse", 5);

        // Capture the OrderMessage argument passed to JmsTemplate so we can inspect it
        ArgumentCaptor<OrderMessage> captor = ArgumentCaptor.forClass(OrderMessage.class);

        // When: the service publishes the order
        messageProducerService.publishOrder(request);

        // Then: capture and verify the published message fields
        verify(jmsTemplate).convertAndSend(anyString(), captor.capture());
        OrderMessage captured = captor.getValue();

        // The DTO fields must be mapped to the domain message correctly
        assertThat(captured.getOrderId()).isEqualTo("ORD-42");
        assertThat(captured.getProduct()).isEqualTo("Wireless Mouse");
        assertThat(captured.getQuantity()).isEqualTo(5);
    }

    @Test
    @DisplayName("publishOrder returns an OrderMessage with auto-generated messageId and createdAt")
    void publishOrder_returnsMessageWithAutoGeneratedFields() {
        // Given: a valid order request
        OrderRequest request = new OrderRequest("ORD-100", "Monitor", 1);

        // When: the service publishes the order
        OrderMessage result = messageProducerService.publishOrder(request);

        // Then: the returned message must have all required fields populated
        assertThat(result).isNotNull();
        assertThat(result.getOrderId()).isEqualTo("ORD-100");
        assertThat(result.getProduct()).isEqualTo("Monitor");
        assertThat(result.getQuantity()).isEqualTo(1);

        // Auto-generated fields must always be non-null and non-blank
        assertThat(result.getMessageId()).isNotNull().isNotBlank();
        assertThat(result.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("publishOrder generates a unique messageId for each call")
    void publishOrder_generatesUniqueMessageIdPerCall() {
        // Given: two identical order requests
        OrderRequest request1 = new OrderRequest("ORD-A", "Widget", 1);
        OrderRequest request2 = new OrderRequest("ORD-A", "Widget", 1);

        // When: both are published
        OrderMessage result1 = messageProducerService.publishOrder(request1);
        OrderMessage result2 = messageProducerService.publishOrder(request2);

        // Then: each message has a distinct messageId (UUID uniqueness guarantee)
        assertThat(result1.getMessageId()).isNotEqualTo(result2.getMessageId());
    }

    @Test
    @DisplayName("publishOrder calls JmsTemplate exactly once per invocation")
    void publishOrder_callsJmsTemplateExactlyOnce() {
        // Given: an order request
        OrderRequest request = new OrderRequest("ORD-ONCE", "Camera", 3);

        // When: published once
        messageProducerService.publishOrder(request);

        // Then: JmsTemplate.convertAndSend must have been called exactly once
        // (not zero times, not multiple times)
        verify(jmsTemplate, times(1)).convertAndSend(anyString(), any(OrderMessage.class));
    }
}
