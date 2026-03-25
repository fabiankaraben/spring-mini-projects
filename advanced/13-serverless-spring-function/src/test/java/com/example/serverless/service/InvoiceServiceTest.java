package com.example.serverless.service;

import com.example.serverless.domain.Invoice;
import com.example.serverless.domain.InvoiceRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link InvoiceService}.
 *
 * <p>These are pure unit tests — no Spring context, no database, no Docker.
 * They test the full invoice generation pipeline: tax calculation +
 * optional discount application combined into a single invoice object.
 *
 * <p>Testing strategy:
 * <ul>
 *   <li>Invoice with no discount code (zero discount)</li>
 *   <li>Invoice with a valid discount code (discount applied)</li>
 *   <li>Invoice ID is generated correctly (starts with "INV-")</li>
 *   <li>finalTotal is correct after tax + discount</li>
 *   <li>issuedAt is non-null</li>
 * </ul>
 */
@DisplayName("InvoiceService Unit Tests")
class InvoiceServiceTest {

    /** The service under test. Real collaborators are used (no mocking needed). */
    private InvoiceService invoiceService;

    @BeforeEach
    void setUp() {
        // InvoiceService depends on TaxService and DiscountService.
        // We use real instances here — their own unit tests cover their logic.
        // This tests the orchestration/integration between the three services.
        TaxService taxService = new TaxService();
        DiscountService discountService = new DiscountService();
        invoiceService = new InvoiceService(taxService, discountService);
    }

    // =========================================================================
    // Invoice generation — no discount
    // =========================================================================

    @Test
    @DisplayName("Invoice without discount: finalTotal = subtotal + tax")
    void generateInvoiceWithoutDiscount() {
        // Given: California order, no discount code
        InvoiceRequest request = new InvoiceRequest(
                "ORD-001", "CUST-1", new BigDecimal("100.00"), "US", "CA", null);

        // When
        Invoice invoice = invoiceService.generate(request);

        // Then
        assertThat(invoice.getInvoiceId()).startsWith("INV-ORD-001-");
        assertThat(invoice.getOrderId()).isEqualTo("ORD-001");
        assertThat(invoice.getCustomerId()).isEqualTo("CUST-1");
        assertThat(invoice.getSubtotal()).isEqualByComparingTo(new BigDecimal("100.00"));

        // California: 8.75% tax on 100.00 = 8.75
        assertThat(invoice.getTaxRate()).isEqualByComparingTo(new BigDecimal("0.0875"));
        assertThat(invoice.getTaxAmount()).isEqualByComparingTo(new BigDecimal("8.75"));
        assertThat(invoice.getTotalBeforeDiscount()).isEqualByComparingTo(new BigDecimal("108.75"));

        // No discount applied
        assertThat(invoice.getDiscountPercent()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(invoice.getDiscountAmount()).isEqualByComparingTo(BigDecimal.ZERO);

        // finalTotal = 108.75 - 0.00 = 108.75
        assertThat(invoice.getFinalTotal()).isEqualByComparingTo(new BigDecimal("108.75"));
        assertThat(invoice.getIssuedAt()).isNotNull();
    }

    @Test
    @DisplayName("Invoice with empty discount code: same as no discount")
    void generateInvoiceWithEmptyDiscountCode() {
        // Given: blank discount code should be treated as no discount
        InvoiceRequest request = new InvoiceRequest(
                "ORD-002", "CUST-2", new BigDecimal("200.00"), "GB", null, "");

        // When
        Invoice invoice = invoiceService.generate(request);

        // Then: GB = 20% tax on 200.00 = 40.00, total = 240.00
        assertThat(invoice.getTaxAmount()).isEqualByComparingTo(new BigDecimal("40.00"));
        assertThat(invoice.getTotalBeforeDiscount()).isEqualByComparingTo(new BigDecimal("240.00"));
        assertThat(invoice.getDiscountAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(invoice.getFinalTotal()).isEqualByComparingTo(new BigDecimal("240.00"));
    }

    // =========================================================================
    // Invoice generation — with discount
    // =========================================================================

    @Test
    @DisplayName("Invoice with SAVE10: discount applied to post-tax total")
    void generateInvoiceWithSave10Discount() {
        // Given: California order with SAVE10 discount
        InvoiceRequest request = new InvoiceRequest(
                "ORD-003", "CUST-3", new BigDecimal("100.00"), "US", "CA", "SAVE10");

        // When
        Invoice invoice = invoiceService.generate(request);

        // Then:
        // Step 1 — tax: 100.00 × 8.75% = 8.75, totalBeforeDiscount = 108.75
        assertThat(invoice.getTaxAmount()).isEqualByComparingTo(new BigDecimal("8.75"));
        assertThat(invoice.getTotalBeforeDiscount()).isEqualByComparingTo(new BigDecimal("108.75"));

        // Step 2 — discount: SAVE10 = 10% off 108.75 = 10.875 → rounds to 10.88 (HALF_UP)
        assertThat(invoice.getDiscountCode()).isEqualTo("SAVE10");
        assertThat(invoice.getDiscountPercent()).isEqualByComparingTo(new BigDecimal("10.00"));
        assertThat(invoice.getDiscountAmount()).isEqualByComparingTo(new BigDecimal("10.88"));

        // Step 3 — finalTotal: 108.75 - 10.88 = 97.87
        assertThat(invoice.getFinalTotal()).isEqualByComparingTo(new BigDecimal("97.87"));
    }

    @Test
    @DisplayName("Invoice with HALFOFF: 50% discount on UK order")
    void generateInvoiceWithHalfOffOnUkOrder() {
        // Given: UK order (20% VAT) with HALFOFF code
        InvoiceRequest request = new InvoiceRequest(
                "ORD-004", "CUST-4", new BigDecimal("200.00"), "GB", null, "HALFOFF");

        // When
        Invoice invoice = invoiceService.generate(request);

        // Then:
        // Step 1 — tax: 200.00 × 20% = 40.00, totalBeforeDiscount = 240.00
        assertThat(invoice.getTaxAmount()).isEqualByComparingTo(new BigDecimal("40.00"));
        assertThat(invoice.getTotalBeforeDiscount()).isEqualByComparingTo(new BigDecimal("240.00"));

        // Step 2 — discount: HALFOFF = 50% off 240.00 = 120.00
        assertThat(invoice.getDiscountPercent()).isEqualByComparingTo(new BigDecimal("50.00"));
        assertThat(invoice.getDiscountAmount()).isEqualByComparingTo(new BigDecimal("120.00"));

        // Step 3 — finalTotal: 240.00 - 120.00 = 120.00
        assertThat(invoice.getFinalTotal()).isEqualByComparingTo(new BigDecimal("120.00"));
    }

    @Test
    @DisplayName("Invoice with unknown discount code: treated as zero discount")
    void generateInvoiceWithUnknownDiscountCode() {
        // Given
        InvoiceRequest request = new InvoiceRequest(
                "ORD-005", "CUST-5", new BigDecimal("100.00"), "US", "NY", "BADCODE");

        // When
        Invoice invoice = invoiceService.generate(request);

        // Then: NY = 8% tax on 100.00 = 8.00, totalBeforeDiscount = 108.00
        assertThat(invoice.getTotalBeforeDiscount()).isEqualByComparingTo(new BigDecimal("108.00"));
        // Unknown code → 0% discount
        assertThat(invoice.getDiscountAmount()).isEqualByComparingTo(BigDecimal.ZERO);
        assertThat(invoice.getFinalTotal()).isEqualByComparingTo(new BigDecimal("108.00"));
    }

    @Test
    @DisplayName("Invoice ID is unique per invocation (contains epoch second)")
    void invoiceIdContainsEpochSecond() {
        InvoiceRequest request = new InvoiceRequest(
                "ORD-006", "CUST-6", new BigDecimal("50.00"), "AU", null, null);

        Invoice invoice = invoiceService.generate(request);

        // Invoice ID format: "INV-{orderId}-{epochSecond}"
        assertThat(invoice.getInvoiceId())
                .startsWith("INV-ORD-006-")
                .matches("INV-ORD-006-\\d+");
    }
}
