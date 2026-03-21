package com.example.springcloudgateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.UUID;

/**
 * A {@link GlobalFilter} that assigns a unique correlation ID to every
 * incoming request and propagates it downstream via an HTTP request header.
 *
 * <p><b>What is a Correlation ID?</b>
 * In a distributed system, a single user-visible operation (e.g. "place an
 * order") may trigger dozens of internal service-to-service calls. Without a
 * shared identifier it is very hard to correlate the logs from the gateway,
 * the product service, the order service, and the payment service into one
 * coherent trace.
 *
 * <p>A correlation ID (also called a trace ID or request ID) is a UUID
 * generated at the edge of the system (the gateway) and included in:
 * <ul>
 *   <li>Every log statement produced by the gateway.</li>
 *   <li>Every downstream HTTP request as the {@code X-Correlation-ID} header,
 *       so downstream services can log the same ID.</li>
 *   <li>The HTTP response returned to the client, so the client can quote the
 *       ID when reporting issues to support.</li>
 * </ul>
 *
 * <p><b>ID reuse:</b>
 * If the incoming request already contains an {@code X-Correlation-ID} header
 * (e.g. set by a load balancer or client SDK), this filter reuses that ID
 * instead of generating a new one. This preserves end-to-end tracing across
 * multiple gateway hops.
 *
 * <p><b>Filter order:</b>
 * This filter uses {@link Ordered#HIGHEST_PRECEDENCE} so it runs first,
 * before the logging filter reads the header and before any other filter
 * might need the correlation ID.
 */
@Component
public class RequestIdGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RequestIdGlobalFilter.class);

    /** The HTTP header name used to propagate the correlation ID. */
    public static final String CORRELATION_ID_HEADER = "X-Correlation-ID";

    /**
     * Generates (or reuses) a correlation ID, attaches it to the request
     * headers forwarded downstream, and also adds it to the response headers
     * returned to the client.
     *
     * <p>Because Spring's {@link ServerHttpRequest} is immutable, we must use
     * {@code exchange.mutate().request(r -> r.header(...)).build()} to create
     * a new immutable request with the added header. The mutated exchange is
     * then passed to the next filter in the chain.
     *
     * @param exchange the current server exchange
     * @param chain    the remaining filter chain
     * @return a {@link Mono<Void>} representing the filter execution
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

        ServerHttpRequest request = exchange.getRequest();

        // Reuse existing correlation ID if the client already sent one;
        // otherwise generate a fresh UUID.
        String correlationId = request.getHeaders().getFirst(CORRELATION_ID_HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
            log.debug("Generated new correlation ID: {}", correlationId);
        } else {
            log.debug("Reusing existing correlation ID: {}", correlationId);
        }

        // Capture as effectively-final for use inside the lambda below.
        final String finalCorrelationId = correlationId;

        // Mutate the request to add the correlation ID header.
        // This mutated request is forwarded to the downstream service.
        ServerHttpRequest mutatedRequest = request.mutate()
                .header(CORRELATION_ID_HEADER, finalCorrelationId)
                .build();

        // Also add the correlation ID to the response headers so the original
        // client can read it and quote it in support tickets.
        exchange.getResponse().getHeaders().add(CORRELATION_ID_HEADER, finalCorrelationId);

        // Replace the exchange's request with the mutated version and continue.
        return chain.filter(exchange.mutate().request(mutatedRequest).build());
    }

    /**
     * Run this filter first (before all other global filters) so the
     * correlation ID is available to every subsequent filter and downstream
     * service.
     *
     * @return {@link Ordered#HIGHEST_PRECEDENCE} — the lowest integer value,
     *         meaning this filter has the highest priority.
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE;
    }
}
