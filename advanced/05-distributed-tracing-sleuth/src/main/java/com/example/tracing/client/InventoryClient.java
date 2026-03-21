package com.example.tracing.client;

import com.example.tracing.model.InventoryResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Feign declarative HTTP client for the Inventory service.
 *
 * <p>Spring Cloud OpenFeign generates a runtime proxy that:
 * <ol>
 *   <li>Looks up the base URL from Spring properties
 *       ({@code inventory.service.url}) at startup.</li>
 *   <li>Injects the current Micrometer Tracing span context into every
 *       outbound HTTP request as B3 propagation headers:
 *       {@code X-B3-TraceId}, {@code X-B3-SpanId}, {@code X-B3-Sampled}.
 *       This allows the receiving service (InventoryController) to
 *       extract the parent span and create a child span under the same trace.</li>
 *   <li>Deserializes the JSON response body into an {@link InventoryResponse}.</li>
 * </ol>
 *
 * <p><b>Why Feign for tracing demonstration?</b>
 * Feign integrates with Micrometer Tracing automatically via the
 * {@code feign-micrometer} module (pulled transitively). Every Feign request
 * is wrapped in a client span named {@code "http GET /inventory/{productId}"},
 * creating a parent→child span relationship that shows up as a waterfall in Zipkin.
 *
 * <p><b>url property:</b>
 * The {@code url} attribute overrides service discovery with a static URL.
 * In tests, {@code @DynamicPropertySource} overrides {@code inventory.service.url}
 * to point at the test server's random port.
 */
@FeignClient(
        name = "inventory-service",
        url = "${inventory.service.url:http://localhost:8080}"
)
public interface InventoryClient {

    /**
     * Checks the inventory level for a specific product.
     *
     * <p>Maps to {@code GET /inventory/{productId}} on the target service.
     * Brave automatically injects the B3 trace context headers into this
     * outbound request before it leaves the JVM.
     *
     * @param productId the product whose stock level is being queried
     * @return the current inventory state including available and reserved units
     */
    @GetMapping("/inventory/{productId}")
    InventoryResponse checkInventory(@PathVariable("productId") String productId);
}
