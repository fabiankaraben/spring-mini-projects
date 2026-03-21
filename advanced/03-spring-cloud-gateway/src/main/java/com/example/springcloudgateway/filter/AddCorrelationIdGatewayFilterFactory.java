package com.example.springcloudgateway.filter;

import org.springframework.cloud.gateway.filter.GatewayFilter;
import org.springframework.cloud.gateway.filter.factory.AbstractGatewayFilterFactory;
import org.springframework.stereotype.Component;

/**
 * A custom per-route {@link GatewayFilter} factory that ensures the
 * {@code X-Correlation-ID} header is present on every request forwarded to
 * a downstream service.
 *
 * <p><b>GatewayFilter vs GlobalFilter:</b>
 * <ul>
 *   <li>A {@code GlobalFilter} (e.g. {@link RequestIdGlobalFilter}) applies
 *       automatically to every route — it cannot be turned off per route.</li>
 *   <li>A {@code GatewayFilter} created by a {@code GatewayFilterFactory} is
 *       applied only to routes that explicitly reference it in their filter
 *       chain (see {@code GatewayRoutesConfig}). This gives fine-grained
 *       control: some routes may need extra headers, others may not.</li>
 * </ul>
 *
 * <p><b>Custom GatewayFilterFactory pattern:</b>
 * Spring Cloud Gateway discovers filter factories by scanning for beans that
 * extend {@link AbstractGatewayFilterFactory}. The bean name (minus the
 * {@code GatewayFilterFactory} suffix) becomes the filter name usable in YAML:
 * <pre>
 *   filters:
 *     - AddCorrelationId
 * </pre>
 * Since we use the Java DSL in this project, we apply it directly:
 * <pre>
 *   .filter(correlationIdFactory.apply(new Config()))
 * </pre>
 *
 * <p><b>Config inner class:</b>
 * Every {@link AbstractGatewayFilterFactory} has a generic type parameter for
 * its configuration POJO. The Config class holds any configurable parameters
 * for the filter. This example has no parameters (the filter always adds the
 * header), but the pattern is shown for educational purposes.
 */
@Component
public class AddCorrelationIdGatewayFilterFactory
        extends AbstractGatewayFilterFactory<AddCorrelationIdGatewayFilterFactory.Config> {

    /** The header name propagated downstream to identify correlated requests. */
    public static final String CORRELATION_HEADER = "X-Correlation-ID";

    /**
     * Registers {@link Config} as this factory's configuration type.
     * Spring Cloud Gateway will deserialize YAML filter configuration into this
     * Config class when the filter is declared in {@code application.yml}.
     */
    public AddCorrelationIdGatewayFilterFactory() {
        super(Config.class);
    }

    /**
     * Creates the actual {@link GatewayFilter} instance from the provided config.
     *
     * <p>The returned filter checks whether the incoming request already carries
     * an {@code X-Correlation-ID} header (set by {@link RequestIdGlobalFilter},
     * which runs first). If it does, the header is left as-is. If for any reason
     * it is missing, this per-route filter adds a fallback value
     * ({@code "fallback-id"}) to ensure downstream services always receive the
     * header. In practice the global filter always sets it first.
     *
     * @param config the configuration for this filter (unused in this simple example)
     * @return the configured {@link GatewayFilter}
     */
    @Override
    public GatewayFilter apply(Config config) {
        return (exchange, chain) -> {
            // Check whether the global filter already added the correlation ID.
            String existingId = exchange.getRequest()
                    .getHeaders()
                    .getFirst(CORRELATION_HEADER);

            if (existingId != null && !existingId.isBlank()) {
                // Header already present — nothing to do; pass the exchange through.
                return chain.filter(exchange);
            }

            // Header missing (should not happen in normal flow, but guard anyway).
            // Mutate the request to add a fallback correlation ID.
            var mutatedRequest = exchange.getRequest().mutate()
                    .header(CORRELATION_HEADER, "fallback-" + System.currentTimeMillis())
                    .build();

            return chain.filter(exchange.mutate().request(mutatedRequest).build());
        };
    }

    /**
     * Configuration POJO for this filter factory.
     *
     * <p>This class is intentionally empty because this filter has no
     * configurable parameters. It exists to satisfy the
     * {@link AbstractGatewayFilterFactory} generic type contract and to
     * demonstrate the standard Spring Cloud Gateway filter factory pattern.
     *
     * <p>In a real filter you would add fields here, e.g.:
     * <pre>
     *   private String headerName = "X-Correlation-ID";
     *   private boolean overwrite = false;
     * </pre>
     */
    public static class Config {
        // No configuration parameters for this simple filter.
        // Fields would be added here for configurable filters.
    }
}
