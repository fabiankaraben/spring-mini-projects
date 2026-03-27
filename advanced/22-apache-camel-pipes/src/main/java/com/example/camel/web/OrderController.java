package com.example.camel.web;

import com.example.camel.domain.Order;
import com.example.camel.domain.OrderResult;
import jakarta.validation.Valid;
import org.apache.camel.ProducerTemplate;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller that accepts incoming order requests and feeds them into the Camel pipeline.
 *
 * <p>This controller is intentionally thin — all business logic lives in the Camel route
 * and its processors.  The controller's only job is to:
 * <ol>
 *   <li>Deserialize the JSON request body into an {@link Order} POJO.</li>
 *   <li>Hand the order to the Camel pipeline via {@link ProducerTemplate#requestBody}.</li>
 *   <li>Return an HTTP response based on the pipeline outcome.</li>
 * </ol>
 *
 * <h3>ProducerTemplate</h3>
 * <p>{@link ProducerTemplate} is the Camel equivalent of Spring's {@code RestTemplate}.
 * It allows any Spring bean to produce (send) messages to a Camel endpoint.
 * Here we use {@code requestBody("direct:orders", order)} which is a <em>synchronous</em>
 * call — it blocks until the pipeline has finished processing or an error has been handled.
 *
 * <p>Calling {@code direct:orders} triggers the {@code order-pipeline} route defined in
 * {@link com.example.camel.route.OrderPipelineRoute}.
 */
@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private static final Logger log = LoggerFactory.getLogger(OrderController.class);

    /**
     * Camel's ProducerTemplate — used to send messages into a Camel route from outside
     * the Camel context (e.g., from a Spring MVC controller).
     *
     * <p>Spring Boot auto-configures a {@code ProducerTemplate} bean when
     * {@code camel-spring-boot-starter} is on the classpath, so we simply inject it here.
     */
    private final ProducerTemplate producerTemplate;

    public OrderController(ProducerTemplate producerTemplate) {
        this.producerTemplate = producerTemplate;
    }

    /**
     * Accepts an order and feeds it into the Camel processing pipeline.
     *
     * <p>The pipeline performs validation → enrichment → classification → dispatch →
     * persistence in sequence.  If validation fails, the pipeline returns a rejection
     * message (HTTP 422); on success it returns HTTP 202 (Accepted).
     *
     * @param order The order payload deserialized from the JSON request body.
     * @return HTTP 202 with an {@link OrderResult} if accepted,
     *         HTTP 422 if validation failed,
     *         HTTP 500 if an unexpected error occurred.
     */
    @PostMapping
    public ResponseEntity<OrderResult> submitOrder(@Valid @RequestBody Order order) {
        log.info("REST: order submission received [orderId={}]", order.getOrderId());

        // Send the order to the Camel direct:orders endpoint synchronously.
        // requestBody() sends the message and waits for the exchange body returned
        // by the last route step.  If onException handled validation failure the
        // body will be the "REJECTED: ..." string set in the error handler.
        Object result = producerTemplate.requestBody("direct:orders", order);

        // If the pipeline returned a rejection string (set by the onException handler),
        // respond with HTTP 422 Unprocessable Entity.
        if (result instanceof String resultStr && resultStr.startsWith("REJECTED:")) {
            log.warn("Order rejected [orderId={}, reason={}]", order.getOrderId(), resultStr);
            return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                    .body(OrderResult.rejected(order.getOrderId(), resultStr));
        }

        // Happy path: the pipeline completed successfully.
        log.info("Order accepted into pipeline [orderId={}]", order.getOrderId());
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(OrderResult.accepted(order.getOrderId()));
    }
}
