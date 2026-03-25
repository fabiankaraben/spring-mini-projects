package com.example.serverless.service;

import com.example.serverless.domain.DiscountRequest;
import com.example.serverless.domain.DiscountResult;
import com.example.serverless.domain.Invoice;
import com.example.serverless.domain.InvoiceRequest;
import com.example.serverless.domain.OrderRequest;
import com.example.serverless.domain.TaxResult;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Domain service that orchestrates tax calculation and discount application
 * to produce a complete {@link Invoice}.
 *
 * <p>This service is the heart of the {@code generateInvoice} function.
 * It delegates to {@link TaxService} and {@link DiscountService} internally,
 * demonstrating how Spring Cloud Function beans can be thin wrappers around
 * well-tested domain services.
 *
 * <p>Invoice generation steps:
 * <ol>
 *   <li>Calculate tax on the order subtotal using {@link TaxService}.</li>
 *   <li>Apply the optional discount code to the post-tax total using {@link DiscountService}.</li>
 *   <li>Assemble the full {@link Invoice} with all line items.</li>
 * </ol>
 */
@Service
public class InvoiceService {

    /** Handles all tax rate lookups and tax amount computation. */
    private final TaxService taxService;

    /** Handles all promotional discount code lookups and discount computation. */
    private final DiscountService discountService;

    /**
     * Constructor injection — preferred over field injection because it makes
     * dependencies explicit and simplifies unit testing.
     *
     * @param taxService      tax calculation service
     * @param discountService discount application service
     */
    public InvoiceService(TaxService taxService, DiscountService discountService) {
        this.taxService = taxService;
        this.discountService = discountService;
    }

    /**
     * Generates a complete invoice for the given request.
     *
     * <p>Processing order:
     * <ol>
     *   <li>Calculate tax on the subtotal.</li>
     *   <li>Apply any discount code to the post-tax total.</li>
     *   <li>Build and return the {@link Invoice}.</li>
     * </ol>
     *
     * @param request the invoice generation request
     * @return a complete {@link Invoice} with all amounts
     */
    public Invoice generate(InvoiceRequest request) {
        // Step 1: Calculate tax on the order subtotal
        OrderRequest orderRequest = new OrderRequest(
                request.getOrderId(),
                request.getCustomerId(),
                request.getSubtotal(),
                request.getCountry(),
                request.getState()
        );
        TaxResult taxResult = taxService.calculate(orderRequest);

        // Step 2: Apply discount to the total-after-tax
        // The discount is applied to the post-tax total (standard e-commerce practice)
        String discountCode = request.getDiscountCode();
        DiscountResult discountResult;
        if (discountCode != null && !discountCode.isBlank()) {
            DiscountRequest discountRequest = new DiscountRequest(
                    request.getOrderId(),
                    taxResult.getTotal(),
                    discountCode
            );
            discountResult = discountService.apply(discountRequest);
        } else {
            // No discount code provided — build a zero-discount result
            discountResult = new DiscountResult(
                    request.getOrderId(),
                    taxResult.getTotal(),
                    null,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO,
                    taxResult.getTotal()
            );
        }

        // Step 3: Assemble the invoice
        Instant now = Instant.now();
        String invoiceId = "INV-" + request.getOrderId() + "-" + now.getEpochSecond();

        return new Invoice(
                invoiceId,
                request.getOrderId(),
                request.getCustomerId(),
                request.getSubtotal(),
                taxResult.getTaxRate(),
                taxResult.getTaxAmount(),
                taxResult.getTotal(),          // totalBeforeDiscount
                discountResult.getDiscountCode(),
                discountResult.getDiscountPercent(),
                discountResult.getDiscountAmount(),
                discountResult.getFinalTotal(), // finalTotal
                now
        );
    }
}
