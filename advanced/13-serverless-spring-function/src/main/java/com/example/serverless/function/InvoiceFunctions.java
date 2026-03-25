package com.example.serverless.function;

import com.example.serverless.domain.AuditEvent;
import com.example.serverless.domain.DiscountRequest;
import com.example.serverless.domain.DiscountResult;
import com.example.serverless.domain.Invoice;
import com.example.serverless.domain.InvoiceRequest;
import com.example.serverless.domain.OrderRequest;
import com.example.serverless.domain.TaxResult;
import com.example.serverless.service.DiscountService;
import com.example.serverless.service.InvoiceService;
import com.example.serverless.service.TaxService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Instant;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * Spring Cloud Function bean definitions for the invoice processing domain.
 *
 * <p>This {@link Configuration} class is the core of the project — it registers
 * plain Java {@link Function} and {@link Consumer} lambdas as Spring beans.
 * Spring Cloud Function discovers these beans and:
 *
 * <ul>
 *   <li>Locally (web): auto-exposes each one at {@code POST /{beanName}}.</li>
 *   <li>AWS Lambda: the adapter routes incoming Lambda events to the selected
 *       bean (determined by the {@code SPRING_CLOUD_FUNCTION_DEFINITION} env var).</li>
 * </ul>
 *
 * <h2>Functions defined here</h2>
 * <ol>
 *   <li>{@code calculateTax}    — {@code Function<OrderRequest, TaxResult>}</li>
 *   <li>{@code applyDiscount}   — {@code Function<DiscountRequest, DiscountResult>}</li>
 *   <li>{@code generateInvoice} — {@code Function<InvoiceRequest, Invoice>}</li>
 *   <li>{@code auditLogger}     — {@code Consumer<AuditEvent>} (no response)</li>
 * </ol>
 *
 * <h2>Why @Bean methods rather than @Component lambdas?</h2>
 * <p>Spring Cloud Function discovers beans of type {@link Function} or {@link Consumer}
 * from the application context. Using {@code @Bean} in a {@code @Configuration} class
 * is the recommended approach because:
 * <ul>
 *   <li>Bean names are exactly the method names (predictable URL paths).</li>
 *   <li>Dependencies are injected as method parameters (clean and testable).</li>
 *   <li>Multiple functions can be defined in one place.</li>
 * </ul>
 *
 * <h2>Function composition</h2>
 * <p>Spring Cloud Function supports composing functions at the HTTP level:
 * {@code POST /calculateTax,applyDiscount} would chain the two functions
 * (the output of calculateTax becomes the input of applyDiscount).
 * Note: type compatibility must hold — the output of the first function
 * must be assignable to the input of the second.
 */
@Configuration
public class InvoiceFunctions {

    private static final Logger log = LoggerFactory.getLogger(InvoiceFunctions.class);

    // =========================================================================
    // Function beans
    // =========================================================================

    /**
     * Tax calculation function.
     *
     * <p>Accepts an {@link OrderRequest} and returns a {@link TaxResult} containing
     * the applicable tax rate, the computed tax amount, and the order total.
     *
     * <p>HTTP endpoint (local): {@code POST /calculateTax}
     * <p>Lambda env var:        {@code SPRING_CLOUD_FUNCTION_DEFINITION=calculateTax}
     *
     * @param taxService injected domain service for tax rate lookup and computation
     * @return a {@link Function} that calculates tax for an order
     */
    @Bean
    public Function<OrderRequest, TaxResult> calculateTax(TaxService taxService) {
        /*
         * The lambda is the actual function body.
         * Spring Cloud Function wraps it with:
         *   - Input deserialization (JSON → OrderRequest)
         *   - Output serialization (TaxResult → JSON)
         *   - Error handling
         */
        return orderRequest -> {
            log.debug("calculateTax invoked for orderId={}", orderRequest.getOrderId());
            TaxResult result = taxService.calculate(orderRequest);
            log.info("Tax calculated: orderId={}, taxRate={}, taxAmount={}, total={}",
                    result.getOrderId(), result.getTaxRate(), result.getTaxAmount(), result.getTotal());
            return result;
        };
    }

    /**
     * Discount application function.
     *
     * <p>Accepts a {@link DiscountRequest} and returns a {@link DiscountResult}
     * containing the discount percentage applied, the monetary amount saved,
     * and the final total after the discount.
     *
     * <p>HTTP endpoint (local): {@code POST /applyDiscount}
     * <p>Lambda env var:        {@code SPRING_CLOUD_FUNCTION_DEFINITION=applyDiscount}
     *
     * @param discountService injected domain service for discount code resolution
     * @return a {@link Function} that applies a discount code to an order total
     */
    @Bean
    public Function<DiscountRequest, DiscountResult> applyDiscount(DiscountService discountService) {
        return discountRequest -> {
            log.debug("applyDiscount invoked for orderId={}, code={}",
                    discountRequest.getOrderId(), discountRequest.getDiscountCode());
            DiscountResult result = discountService.apply(discountRequest);
            log.info("Discount applied: orderId={}, code={}, percent={}%, amount={}, finalTotal={}",
                    result.getOrderId(), result.getDiscountCode(), result.getDiscountPercent(),
                    result.getDiscountAmount(), result.getFinalTotal());
            return result;
        };
    }

    /**
     * Invoice generation function.
     *
     * <p>Accepts an {@link InvoiceRequest} and returns a complete {@link Invoice}
     * combining tax calculation and discount application in a single call.
     *
     * <p>HTTP endpoint (local): {@code POST /generateInvoice}
     * <p>Lambda env var:        {@code SPRING_CLOUD_FUNCTION_DEFINITION=generateInvoice}
     *
     * @param invoiceService injected orchestration service
     * @return a {@link Function} that generates a complete invoice
     */
    @Bean
    public Function<InvoiceRequest, Invoice> generateInvoice(InvoiceService invoiceService) {
        return invoiceRequest -> {
            log.debug("generateInvoice invoked for orderId={}", invoiceRequest.getOrderId());
            Invoice invoice = invoiceService.generate(invoiceRequest);
            log.info("Invoice generated: invoiceId={}, orderId={}, finalTotal={}",
                    invoice.getInvoiceId(), invoice.getOrderId(), invoice.getFinalTotal());
            return invoice;
        };
    }

    // =========================================================================
    // Consumer beans
    // =========================================================================

    /**
     * Audit logging consumer.
     *
     * <p>Accepts an {@link AuditEvent} and logs it. Returns nothing (void).
     *
     * <p>Spring Cloud Function's web adapter returns HTTP 202 Accepted for
     * {@link Consumer} invocations because they are fire-and-forget with no
     * meaningful return value.
     *
     * <p>HTTP endpoint (local): {@code POST /auditLogger}
     * <p>Lambda env var:        {@code SPRING_CLOUD_FUNCTION_DEFINITION=auditLogger}
     *
     * <p>In a production system this consumer might:
     * <ul>
     *   <li>Write to an audit database table.</li>
     *   <li>Publish to an AWS SQS or SNS topic.</li>
     *   <li>Send to a log aggregation service (e.g., Splunk, Datadog).</li>
     * </ul>
     *
     * @return a {@link Consumer} that logs audit events
     */
    @Bean
    public Consumer<AuditEvent> auditLogger() {
        return auditEvent -> {
            // Populate occurredAt if the caller did not set it
            Instant timestamp = auditEvent.getOccurredAt() != null
                    ? auditEvent.getOccurredAt()
                    : Instant.now();

            // In a real system, persist to an audit table or publish to a queue.
            // Here we log at INFO level to demonstrate the consumer is working.
            log.info("[AUDIT] type={} | orderId={} | actor={} | at={} | details={}",
                    auditEvent.getEventType(),
                    auditEvent.getOrderId(),
                    auditEvent.getActor(),
                    timestamp,
                    auditEvent.getDetails());
        };
    }
}
