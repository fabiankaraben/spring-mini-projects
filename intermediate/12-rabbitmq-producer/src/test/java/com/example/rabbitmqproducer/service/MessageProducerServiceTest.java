package com.example.rabbitmqproducer.service;

import com.example.rabbitmqproducer.domain.OrderMessage;
import com.example.rabbitmqproducer.dto.OrderRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
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
 * <p>These tests exercise the service's business logic in pure isolation:
 * <ul>
 *   <li>The {@link RabbitTemplate} is replaced with a Mockito mock, so no real
 *       RabbitMQ broker connection is needed. Tests run in milliseconds without
 *       Docker.</li>
 *   <li>No Spring context is loaded — {@link ExtendWith}({@link MockitoExtension}.class)
 *       initialises Mockito annotations only, keeping startup time near zero.</li>
 *   <li>{@link ReflectionTestUtils#setField} injects the {@code @Value}-annotated
 *       fields ({@code exchangeName}, {@code routingKey}) that would normally be
 *       populated by the Spring environment during context startup.</li>
 *   <li>Each test follows the Given / When / Then (Arrange / Act / Assert) pattern
 *       to make intent and expectations explicit for educational clarity.</li>
 * </ul>
 *
 * <p>Integration tests (see {@link com.example.rabbitmqproducer.MessageProducerIntegrationTest})
 * cover the full stack with a real RabbitMQ container via Testcontainers.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("MessageProducerService unit tests")
class MessageProducerServiceTest {

    /**
     * Mockito mock of the RabbitTemplate – no real broker is involved.
     * All interactions with RabbitMQ are simulated via Mockito stubs and
     * verified with {@code verify(...)}.
     */
    @Mock
    private RabbitTemplate rabbitTemplate;

    /**
     * The class under test.
     * {@code @InjectMocks} asks Mockito to create a {@link MessageProducerService}
     * instance and inject the {@code @Mock} fields (rabbitTemplate) via constructor.
     */
    @InjectMocks
    private MessageProducerService messageProducerService;

    // ── Test constants ────────────────────────────────────────────────────────────

    private static final String TEST_EXCHANGE = "test.exchange";
    private static final String TEST_ROUTING_KEY = "test.routing.key";

    // ── Setup ─────────────────────────────────────────────────────────────────────

    /**
     * Inject the {@code @Value}-annotated fields that Spring would normally populate.
     *
     * <p>{@link ReflectionTestUtils#setField} bypasses Spring's {@code @Value}
     * injection mechanism and writes directly to private fields. This is necessary
     * because we are not loading a Spring context in these unit tests.
     */
    @BeforeEach
    void setUp() {
        // Simulate Spring's @Value injection for fields that have no Spring context
        ReflectionTestUtils.setField(messageProducerService, "exchangeName", TEST_EXCHANGE);
        ReflectionTestUtils.setField(messageProducerService, "routingKey", TEST_ROUTING_KEY);
    }

    // ── publishOrder ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("publishOrder delegates to RabbitTemplate with correct exchange and routing key")
    void publishOrder_callsRabbitTemplateWithCorrectExchangeAndRoutingKey() {
        // Given: a valid order request
        OrderRequest request = new OrderRequest("ORD-001", "Laptop", 2);

        // When: the service publishes the order
        messageProducerService.publishOrder(request);

        // Then: RabbitTemplate.convertAndSend was called exactly once with the correct exchange and routing key
        verify(rabbitTemplate, times(1))
                .convertAndSend(eq(TEST_EXCHANGE), eq(TEST_ROUTING_KEY), any(OrderMessage.class));
    }

    @Test
    @DisplayName("publishOrder maps DTO fields correctly to the domain OrderMessage")
    void publishOrder_mapsRequestFieldsToOrderMessage() {
        // Given: a specific order request
        OrderRequest request = new OrderRequest("ORD-42", "Wireless Mouse", 5);

        // Capture the OrderMessage argument passed to RabbitTemplate so we can inspect it
        ArgumentCaptor<OrderMessage> captor = ArgumentCaptor.forClass(OrderMessage.class);

        // When: the service publishes the order
        messageProducerService.publishOrder(request);

        // Then: capture and verify the published message fields
        verify(rabbitTemplate).convertAndSend(anyString(), anyString(), captor.capture());
        OrderMessage captured = captor.getValue();

        // The DTO fields must be mapped to the domain message correctly
        assertThat(captured.getOrderId()).isEqualTo("ORD-42");
        assertThat(captured.getProduct()).isEqualTo("Wireless Mouse");
        assertThat(captured.getQuantity()).isEqualTo(5);
    }

    @Test
    @DisplayName("publishOrder auto-generates a non-null messageId")
    void publishOrder_autoGeneratesMessageId() {
        // Given: a valid order request
        OrderRequest request = new OrderRequest("ORD-100", "Keyboard", 1);

        ArgumentCaptor<OrderMessage> captor = ArgumentCaptor.forClass(OrderMessage.class);

        // When
        messageProducerService.publishOrder(request);

        // Then: the service must generate a non-blank UUID for the message
        verify(rabbitTemplate).convertAndSend(anyString(), anyString(), captor.capture());
        assertThat(captor.getValue().getMessageId())
                .isNotNull()
                .isNotBlank();
    }

    @Test
    @DisplayName("publishOrder auto-generates a non-null createdAt timestamp")
    void publishOrder_autoGeneratesCreatedAt() {
        // Given: a valid order request
        OrderRequest request = new OrderRequest("ORD-200", "Monitor", 1);

        ArgumentCaptor<OrderMessage> captor = ArgumentCaptor.forClass(OrderMessage.class);

        // When
        messageProducerService.publishOrder(request);

        // Then: the service must set a createdAt timestamp on the domain message
        verify(rabbitTemplate).convertAndSend(anyString(), anyString(), captor.capture());
        assertThat(captor.getValue().getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("publishOrder returns the OrderMessage that was published")
    void publishOrder_returnsPublishedOrderMessage() {
        // Given: a valid order request
        OrderRequest request = new OrderRequest("ORD-300", "Headphones", 3);

        // When: publish the order
        OrderMessage result = messageProducerService.publishOrder(request);

        // Then: the returned object must carry the request data (plus auto-generated fields)
        assertThat(result).isNotNull();
        assertThat(result.getOrderId()).isEqualTo("ORD-300");
        assertThat(result.getProduct()).isEqualTo("Headphones");
        assertThat(result.getQuantity()).isEqualTo(3);
        assertThat(result.getMessageId()).isNotNull();
        assertThat(result.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("publishOrder generates unique messageIds for each published message")
    void publishOrder_generatesUniqueMessageIdPerCall() {
        // Given: two separate order requests
        OrderRequest first = new OrderRequest("ORD-A", "Product A", 1);
        OrderRequest second = new OrderRequest("ORD-B", "Product B", 1);

        // When: both are published
        OrderMessage firstResult = messageProducerService.publishOrder(first);
        OrderMessage secondResult = messageProducerService.publishOrder(second);

        // Then: the messageIds must be different (each UUID is randomly generated)
        assertThat(firstResult.getMessageId())
                .isNotEqualTo(secondResult.getMessageId());
    }
}
