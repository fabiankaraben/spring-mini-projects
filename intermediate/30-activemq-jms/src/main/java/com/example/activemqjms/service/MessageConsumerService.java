package com.example.activemqjms.service;

import com.example.activemqjms.domain.OrderMessage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jms.annotation.JmsListener;
import org.springframework.stereotype.Service;

/**
 * JMS message consumer that listens to the orders queue and delegates
 * received messages to the domain processing layer.
 *
 * <p>{@link JmsListener} registers this method as an asynchronous listener
 * on the named queue. Spring JMS:
 * <ol>
 *   <li>Creates a background thread pool (via {@link org.springframework.jms.config.DefaultJmsListenerContainerFactory})
 *       that continuously polls the broker for new messages.</li>
 *   <li>When a message arrives, it deserialises the JSON body back into an
 *       {@link OrderMessage} using the {@link org.springframework.jms.support.converter.MappingJackson2MessageConverter}
 *       configured in {@link com.example.activemqjms.config.JmsConfig}.</li>
 *   <li>Calls the annotated method with the deserialised object.</li>
 *   <li>If the method returns without throwing an exception, the message is
 *       <em>acknowledged</em> and removed from the queue (AUTO_ACKNOWLEDGE mode).</li>
 *   <li>If the method throws an exception, the message is <em>redelivered</em>
 *       (at-least-once delivery guarantee).</li>
 * </ol>
 *
 * <h2>Why separate the transport concern from the domain logic?</h2>
 * <p>This class is intentionally thin — it only handles the JMS transport concern
 * (receiving and deserialising the message). All domain logic is delegated to
 * {@link OrderProcessingService}, which:
 * <ul>
 *   <li>Is easier to unit-test without any JMS infrastructure.</li>
 *   <li>Can be reused from non-JMS code paths (REST endpoints, scheduled jobs, etc.).</li>
 * </ul>
 *
 * <h2>Concurrency note</h2>
 * <p>By default, Spring JMS runs listener methods in a single-threaded listener
 * container. If parallel processing is needed, the container factory's
 * {@code concurrency} setting can be increased (e.g. {@code "3-10"} means
 * 3 concurrent consumers, scaling up to 10 under load).
 */
@Service
public class MessageConsumerService {

    private static final Logger log = LoggerFactory.getLogger(MessageConsumerService.class);

    /**
     * Domain service that contains the actual order processing logic.
     * Injected via constructor to facilitate unit testing.
     */
    private final OrderProcessingService orderProcessingService;

    /**
     * Constructor injection ensures {@link OrderProcessingService} is
     * always available when the listener fires.
     *
     * @param orderProcessingService the domain layer that processes each order
     */
    public MessageConsumerService(OrderProcessingService orderProcessingService) {
        this.orderProcessingService = orderProcessingService;
    }

    /**
     * Receive an {@link OrderMessage} from the JMS orders queue and process it.
     *
     * <p>The {@code destination} value is resolved against the Spring
     * {@link org.springframework.core.env.Environment} at startup, so changing
     * the queue name in {@code application.yml} does not require a code change.
     *
     * <p>Spring JMS automatically deserialises the incoming JSON text message
     * to an {@link OrderMessage} instance before calling this method, using
     * the converter registered in {@link com.example.activemqjms.config.JmsConfig}.
     *
     * @param message the deserialised order message from the queue
     */
    @JmsListener(destination = "${app.jms.orders-queue}",
                 containerFactory = "jmsListenerContainerFactory")
    public void receiveOrder(OrderMessage message) {
        log.info("Received order from JMS queue: {}", message);

        // Delegate all domain logic to the processing service.
        // If processOrder throws, the JMS container will redeliver the message.
        orderProcessingService.processOrder(message);
    }
}
