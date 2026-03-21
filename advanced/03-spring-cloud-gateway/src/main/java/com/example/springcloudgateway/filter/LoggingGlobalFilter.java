package com.example.springcloudgateway.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * A {@link GlobalFilter} that logs every request entering and every response
 * leaving the API Gateway.
 *
 * <p><b>What is a GlobalFilter?</b>
 * A {@link GlobalFilter} is applied to <em>every</em> route configured in the
 * gateway — there is no per-route opt-in. This makes it ideal for cross-cutting
 * concerns like logging, authentication checks, and metrics collection.
 *
 * <p>Compare this with a {@code GatewayFilter}, which is applied only to the
 * specific routes where it is configured (see {@code GatewayRoutesConfig}).
 *
 * <p><b>Reactive pipeline execution:</b>
 * Spring Cloud Gateway is built on Project Reactor (WebFlux). The filter
 * chain is a reactive pipeline:
 * <pre>
 *   filter(exchange, chain) {
 *       // -- PRE logic (runs before forwarding to downstream) --
 *       return chain.filter(exchange)  // forward to downstream
 *           .then(Mono.fromRunnable(() -> {
 *               // -- POST logic (runs after downstream responds) --
 *           }));
 *   }
 * </pre>
 * Code before {@code chain.filter(exchange)} runs on the way IN (pre-filter).
 * Code inside {@code .then()} / {@code .doFinally()} runs on the way OUT
 * (post-filter), after the downstream service has responded.
 *
 * <p><b>Ordering ({@link Ordered}):</b>
 * When multiple {@code GlobalFilter}s are registered, Spring Cloud Gateway
 * executes them in order of their {@code getOrder()} value. Lower values run
 * first (higher priority). This filter uses {@code Ordered.HIGHEST_PRECEDENCE + 1}
 * so it runs very early (almost first), capturing requests before other filters
 * transform them, while still letting the correlation-ID filter (at
 * {@code HIGHEST_PRECEDENCE}) add its header first.
 */
@Component
public class LoggingGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(LoggingGlobalFilter.class);

    /**
     * Intercepts every gateway request, logs request details, and then
     * logs response details after the downstream call completes.
     *
     * @param exchange the current server exchange (request + response)
     * @param chain    the filter chain to delegate to
     * @return a {@link Mono<Void>} that completes when the exchange is done
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {

        ServerHttpRequest request = exchange.getRequest();

        // Record the start time so we can calculate response latency later.
        long startTime = Instant.now().toEpochMilli();

        // --- PRE-filter logic: log the incoming request ---
        log.info("[GATEWAY REQUEST ] method={} path={} correlationId={}",
                request.getMethod(),
                request.getURI().getPath(),
                // Read the correlation ID added by RequestIdGlobalFilter (if present).
                request.getHeaders().getFirst("X-Correlation-ID"));

        // Delegate to the next filter in the chain (eventually the downstream proxy).
        // .then() registers a callback that runs AFTER the chain completes.
        return chain.filter(exchange)
                .then(Mono.fromRunnable(() -> {

                    // --- POST-filter logic: log the outgoing response ---
                    ServerHttpResponse response = exchange.getResponse();
                    long latencyMs = Instant.now().toEpochMilli() - startTime;

                    log.info("[GATEWAY RESPONSE] method={} path={} status={} latencyMs={}",
                            request.getMethod(),
                            request.getURI().getPath(),
                            response.getStatusCode(),
                            latencyMs);
                }));
    }

    /**
     * Execution order for this filter.
     *
     * <p>Using {@code HIGHEST_PRECEDENCE + 1} places this filter very early
     * in the chain — right after the correlation-ID filter — so it captures
     * requests before most other filters transform them.
     *
     * @return the order value; lower = higher priority
     */
    @Override
    public int getOrder() {
        return Ordered.HIGHEST_PRECEDENCE + 1;
    }
}
