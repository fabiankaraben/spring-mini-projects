package com.example.tracing.controller;

import com.example.tracing.model.TraceInfo;
import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Diagnostic controller that exposes the current Micrometer Tracing context.
 *
 * <p>This endpoint is purely educational — it shows the caller exactly which
 * trace and span IDs are active <em>while serving their own request</em>.
 * This makes the tracing system tangible: you can call this endpoint, see the
 * traceId in the JSON response, and immediately paste it into the Zipkin UI
 * (http://localhost:9411) to find the corresponding trace.
 *
 * <p><b>How the Tracer API works:</b>
 * <ul>
 *   <li>{@code tracer.currentSpan()} — returns the span that is currently active
 *       on this thread, or {@code null} if no span is active (e.g., if tracing is
 *       disabled or sampling probability is 0).</li>
 *   <li>{@code span.context().traceId()} — 128-bit hex trace ID (32 chars) or
 *       64-bit hex (16 chars) depending on Brave configuration.</li>
 *   <li>{@code span.context().spanId()} — 64-bit hex span ID (16 chars).</li>
 *   <li>{@code span.context().parentId()} — parent span ID; {@code null} for root spans.</li>
 *   <li>{@code span.context().sampled()} — {@code Boolean.TRUE} if this trace is
 *       being exported to the backend; {@code Boolean.FALSE} if suppressed;
 *       {@code null} if deferred (the tracing backend decides).</li>
 * </ul>
 *
 * <p><b>MDC integration:</b>
 * Brave's {@code MDCScopeDecorator} (auto-configured by Spring Boot) writes the
 * traceId, spanId, and parentId into SLF4J MDC under the keys {@code traceId},
 * {@code spanId}, and {@code parentId}. This means every log line emitted during
 * a traced request automatically includes these IDs — visible in the log output as
 * {@code [traceId=abc123, spanId=def456, parentId=...]}.
 */
@RestController
@RequestMapping("/trace")
public class TraceInfoController {

    private static final Logger log = LoggerFactory.getLogger(TraceInfoController.class);

    /** Micrometer Tracing API — auto-configured by Spring Boot. */
    private final Tracer tracer;

    /** The Spring application name, included in the response for multi-service clarity. */
    private final String serviceName;

    public TraceInfoController(Tracer tracer,
                               @Value("${spring.application.name}") String serviceName) {
        this.tracer = tracer;
        this.serviceName = serviceName;
    }

    /**
     * Returns the trace context that is active while handling this very request.
     *
     * <p>The response includes the traceId, spanId, parentId, sampled flag, and
     * service name. The traceId can be used directly in the Zipkin search box at
     * http://localhost:9411 to find this request's trace.
     *
     * @return HTTP 200 with the current {@link TraceInfo} as JSON
     */
    @GetMapping("/current")
    public TraceInfo currentTrace() {
        // Get the span that Spring MVC auto-instrumentation created for this request.
        // Because tracing auto-configuration is active, this should never be null
        // (as long as sampling probability > 0 or the request carries a trace header).
        Span currentSpan = tracer.currentSpan();

        if (currentSpan == null) {
            log.warn("No active span found — tracing may be disabled or sampling rate is 0");
            return new TraceInfo("no-trace", "no-span", "none", false, serviceName);
        }

        String traceId  = currentSpan.context().traceId();
        String spanId   = currentSpan.context().spanId();
        String parentId = currentSpan.context().parentId() != null
                ? currentSpan.context().parentId()
                : "none";
        // sampled() returns Boolean (nullable); default to false if null (deferred sampling)
        boolean sampled = Boolean.TRUE.equals(currentSpan.context().sampled());

        log.info("Returning trace info [traceId={} spanId={} parentId={} sampled={}]",
                traceId, spanId, parentId, sampled);

        return new TraceInfo(traceId, spanId, parentId, sampled, serviceName);
    }
}
