package com.example.tracing;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.openfeign.EnableFeignClients;

/**
 * Entry point for the Distributed Tracing mini-project.
 *
 * <p><b>What this application demonstrates:</b>
 * This Spring Boot application shows how Micrometer Tracing (the Spring Boot 3
 * successor to Spring Cloud Sleuth) automatically instruments every HTTP request
 * with a <em>trace ID</em> and <em>span ID</em>, and propagates them across
 * simulated inter-service calls using Feign HTTP clients.
 *
 * <p><b>Key tracing concepts:</b>
 * <ul>
 *   <li><b>Trace</b> — a directed acyclic graph of Spans representing one end-to-end
 *       operation (e.g., a user placing an order). All spans in a trace share the same
 *       {@code traceId}.</li>
 *   <li><b>Span</b> — a single unit of work (e.g., one HTTP request or one database call).
 *       Each span has a unique {@code spanId}, a parent {@code spanId} (except the root span),
 *       start/end timestamps, and optional tags and events.</li>
 *   <li><b>Context propagation</b> — when this service calls another service via HTTP,
 *       Brave (the underlying tracer) injects the current span context into outbound
 *       request headers ({@code X-B3-TraceId}, {@code X-B3-SpanId}, or the W3C
 *       {@code traceparent} header). The receiving service extracts these headers and
 *       continues the same trace instead of starting a new one.</li>
 * </ul>
 *
 * <p><b>Application structure:</b>
 * <pre>
 *   OrderController       — handles POST /orders — root span created by MVC instrumentation
 *     └─ OrderService      — creates a child span "process-order" programmatically
 *          └─ InventoryClient (Feign) — makes an HTTP call to /inventory/{productId}
 *               └─ InventoryController  — handled as a child span in the same trace
 *
 *   ProductController     — handles GET /products/{id} — demonstrates manual span tags
 *   TraceInfoController   — GET /trace/current — returns current trace/span IDs at runtime
 * </pre>
 *
 * <p><b>Trace export:</b>
 * Completed spans are exported asynchronously to a Zipkin server (configured via
 * {@code management.zipkin.tracing.endpoint}). In Docker Compose the Zipkin UI is
 * accessible at http://localhost:9411. Locally, set {@code spring.zipkin.base-url}
 * or run Zipkin in Docker and adjust {@code application.yml}.
 *
 * <p><b>@EnableFeignClients:</b>
 * Activates the Spring Cloud OpenFeign client scanner in the current package tree.
 * Without this annotation, interfaces annotated with {@code @FeignClient} are not
 * registered as Spring beans.
 */
@SpringBootApplication
@EnableFeignClients
public class DistributedTracingApplication {

    public static void main(String[] args) {
        SpringApplication.run(DistributedTracingApplication.class, args);
    }
}
