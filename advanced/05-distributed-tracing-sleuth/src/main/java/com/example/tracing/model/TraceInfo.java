package com.example.tracing.model;

/**
 * Snapshot of the current Micrometer Tracing context.
 *
 * <p>Returned by {@code GET /trace/current} for educational purposes —
 * it lets callers inspect the trace/span IDs that are active <em>while
 * handling their own request</em>.
 *
 * <p><b>How are these IDs obtained?</b>
 * Micrometer's {@code Tracer} bean (auto-configured by Spring Boot when
 * {@code micrometer-tracing-bridge-brave} is on the classpath) exposes the
 * current span via {@code tracer.currentSpan()}. From a span you can read:
 * <ul>
 *   <li>{@code span.context().traceId()} — 128-bit or 64-bit hex trace ID</li>
 *   <li>{@code span.context().spanId()} — 64-bit hex span ID</li>
 *   <li>{@code span.context().parentId()} — parent span ID (null for root spans)</li>
 * </ul>
 *
 * <p><b>Sampled flag:</b>
 * {@code sampled = true} means this trace will be exported to Zipkin.
 * {@code sampled = false} means Brave still tracks the IDs (they appear in logs)
 * but the span will NOT be sent to the tracing backend.
 * The sample rate is controlled by {@code management.tracing.sampling.probability}.
 *
 * @param traceId   hex-encoded 128-bit trace ID (shared by all spans in this trace)
 * @param spanId    hex-encoded 64-bit span ID (unique to this specific operation)
 * @param parentId  hex-encoded parent span ID, or "none" if this is the root span
 * @param sampled   whether this trace is being exported to the tracing backend
 * @param service   the Spring application name (for correlating across services)
 */
public record TraceInfo(
        String traceId,
        String spanId,
        String parentId,
        boolean sampled,
        String service
) {}
