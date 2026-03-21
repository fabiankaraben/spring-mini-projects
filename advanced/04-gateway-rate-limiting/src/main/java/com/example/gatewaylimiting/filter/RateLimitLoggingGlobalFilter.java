package com.example.gatewaylimiting.filter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

/**
 * A {@link GlobalFilter} that logs rate limit information for every request.
 *
 * <p><b>What is a GlobalFilter?</b>
 * A {@code GlobalFilter} is applied automatically to every route without needing
 * to be referenced explicitly in each route's filter chain. Spring Cloud Gateway
 * orders all global filters by their {@link Ordered#getOrder()} value — lower
 * values run earlier in the pre-processing phase (before forwarding) and later
 * in the post-processing phase (after the downstream response arrives).
 *
 * <p><b>What this filter does:</b>
 * <ol>
 *   <li><b>Pre-processing</b>: logs the incoming request method, path, and
 *       remote address so you can track every request entering the gateway.</li>
 *   <li><b>Post-processing</b>: after the response is written, logs the HTTP
 *       status. Specifically flags HTTP 429 responses so rate limiting events
 *       are clearly visible in the logs.</li>
 * </ol>
 *
 * <p><b>Reactive chain pattern:</b>
 * The {@code doOnSuccess} and {@code doFinally} operators are used to tap into
 * the reactive pipeline without blocking. They execute callbacks when the
 * {@code Mono<Void>} completes, allowing post-response logging in a non-blocking way.
 */
@Component
public class RateLimitLoggingGlobalFilter implements GlobalFilter, Ordered {

    private static final Logger log = LoggerFactory.getLogger(RateLimitLoggingGlobalFilter.class);

    /**
     * Returns the order of this filter.
     *
     * <p>A value of {@code -1} places this filter near the beginning of the
     * global filter chain, ensuring the pre-processing log runs before most
     * other filters (including the rate limiter itself). The post-processing
     * part runs at the same relative position but after the response is ready.
     *
     * @return the filter order
     */
    @Override
    public int getOrder() {
        return -1;
    }

    /**
     * Filters the exchange by logging request entry and response status.
     *
     * <p>The filter structure follows the standard WebFlux GlobalFilter pattern:
     * <pre>
     *   // --- PRE-processing (before downstream call) ---
     *   log.info("Incoming request: ...");
     *
     *   // Delegate to the next filter/handler in the chain.
     *   return chain.filter(exchange)
     *       // --- POST-processing (after downstream call completes) ---
     *       .doFinally(signal -> log.info("Response status: ..."));
     * </pre>
     *
     * @param exchange the current server-side HTTP request/response
     * @param chain    the remaining filter chain to execute
     * @return a {@link Mono} that completes when the filter chain finishes
     */
    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        // Pre-processing: log the incoming request before any rate limiting decision.
        String method = exchange.getRequest().getMethod().name();
        String path   = exchange.getRequest().getURI().getPath();
        String remote = exchange.getRequest().getRemoteAddress() != null
                ? exchange.getRequest().getRemoteAddress().getAddress().getHostAddress()
                : "unknown";
        log.info("Incoming request: {} {} from {}", method, path, remote);

        // Delegate to the rest of the filter chain (including the rate limiter).
        return chain.filter(exchange)
                .doFinally(signal -> {
                    // Post-processing: log the HTTP response status code.
                    HttpStatus status = (HttpStatus) exchange.getResponse().getStatusCode();
                    if (status == HttpStatus.TOO_MANY_REQUESTS) {
                        // Highlight rate limit rejections prominently in the logs.
                        log.warn("RATE LIMIT EXCEEDED: {} {} from {} → HTTP 429",
                                method, path, remote);
                    } else {
                        log.info("Response: {} {} → {}", method, path,
                                status != null ? status.value() : "unknown");
                    }
                });
    }
}
