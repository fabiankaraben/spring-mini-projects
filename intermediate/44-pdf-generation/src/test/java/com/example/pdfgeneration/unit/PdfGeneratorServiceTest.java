package com.example.pdfgeneration.unit;

import com.example.pdfgeneration.domain.Invoice;
import com.example.pdfgeneration.domain.InvoiceLineItem;
import com.example.pdfgeneration.service.PdfGeneratorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link PdfGeneratorService}.
 *
 * <p>These tests verify that the PDF generation logic produces valid, non-empty
 * byte arrays without starting a Spring application context, loading any database,
 * or spawning Docker containers. They run entirely in memory.
 *
 * <h2>What we test</h2>
 * <ul>
 *   <li>That {@code generateInvoicePdf} returns a non-null, non-empty byte array
 *       that begins with the PDF magic bytes ({@code %PDF}).</li>
 *   <li>That the output is valid even with an empty line-items list, with multiple
 *       line items, and with or without notes.</li>
 *   <li>That {@code generateInvoiceReportPdf} similarly produces a valid PDF
 *       for an empty list and a populated list of invoices.</li>
 * </ul>
 *
 * <h2>PDF magic bytes</h2>
 * Every valid PDF file starts with the ASCII sequence {@code %PDF-} (bytes
 * {@code 0x25 0x50 0x44 0x46 0x2D}). Checking for this header is the
 * simplest way to confirm a byte array is a real PDF without a full PDF parser.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("PdfGeneratorService – Unit Tests")
class PdfGeneratorServiceTest {

    /**
     * The class under test. Instantiated directly (no Spring context) so the
     * test runs fast and without infrastructure dependencies.
     */
    private PdfGeneratorService pdfGeneratorService;

    /**
     * A reusable invoice fixture used across multiple test methods.
     */
    private Invoice sampleInvoice;

    /**
     * Sets up a fresh service instance and a sample invoice before each test.
     */
    @BeforeEach
    void setUp() {
        pdfGeneratorService = new PdfGeneratorService();

        // Build a sample invoice with realistic data
        sampleInvoice = new Invoice(
                "INV-2024-001",
                "Acme Corporation",
                "billing@acme.com",
                new BigDecimal("1500.00"),
                "USD",
                LocalDate.of(2024, 6, 15),
                "Payment due within 30 days."
        );
    }

    // ── generateInvoicePdf tests ───────────────────────────────────────────

    @Test
    @DisplayName("generateInvoicePdf should return a non-null byte array")
    void generateInvoicePdf_shouldReturnNonNullBytes() {
        // Arrange: one line item
        List<InvoiceLineItem> lineItems = List.of(
                new InvoiceLineItem(sampleInvoice, "Consulting Services", 10,
                        new BigDecimal("150.00"))
        );

        // Act
        byte[] result = pdfGeneratorService.generateInvoicePdf(sampleInvoice, lineItems);

        // Assert: the result must never be null
        assertThat(result).isNotNull();
    }

    @Test
    @DisplayName("generateInvoicePdf should return a non-empty byte array")
    void generateInvoicePdf_shouldReturnNonEmptyBytes() {
        List<InvoiceLineItem> lineItems = List.of(
                new InvoiceLineItem(sampleInvoice, "Software License", 1,
                        new BigDecimal("999.99"))
        );

        byte[] result = pdfGeneratorService.generateInvoicePdf(sampleInvoice, lineItems);

        // A real PDF is always several hundred bytes at minimum
        assertThat(result).isNotEmpty();
        assertThat(result.length).isGreaterThan(100);
    }

    @Test
    @DisplayName("generateInvoicePdf output should start with the PDF magic bytes (%PDF)")
    void generateInvoicePdf_outputShouldStartWithPdfMagicBytes() {
        List<InvoiceLineItem> lineItems = List.of(
                new InvoiceLineItem(sampleInvoice, "Design Work", 3,
                        new BigDecimal("200.00"))
        );

        byte[] result = pdfGeneratorService.generateInvoicePdf(sampleInvoice, lineItems);

        // The first 4 bytes of any valid PDF must be 0x25 0x50 0x44 0x46 ("%PDF")
        assertThat(result[0]).isEqualTo((byte) 0x25); // '%'
        assertThat(result[1]).isEqualTo((byte) 0x50); // 'P'
        assertThat(result[2]).isEqualTo((byte) 0x44); // 'D'
        assertThat(result[3]).isEqualTo((byte) 0x46); // 'F'
    }

    @Test
    @DisplayName("generateInvoicePdf should work with multiple line items")
    void generateInvoicePdf_shouldHandleMultipleLineItems() {
        // Arrange: several items of different quantities and prices
        List<InvoiceLineItem> lineItems = List.of(
                new InvoiceLineItem(sampleInvoice, "Backend Development", 20,
                        new BigDecimal("120.00")),
                new InvoiceLineItem(sampleInvoice, "Frontend Development", 15,
                        new BigDecimal("100.00")),
                new InvoiceLineItem(sampleInvoice, "DevOps Setup", 5,
                        new BigDecimal("180.00")),
                new InvoiceLineItem(sampleInvoice, "Project Management", 8,
                        new BigDecimal("90.00"))
        );

        byte[] result = pdfGeneratorService.generateInvoicePdf(sampleInvoice, lineItems);

        assertThat(result).isNotNull().isNotEmpty();
        assertThat(result.length).isGreaterThan(100);
        // Verify PDF header
        assertThat(new String(result, 0, 4)).isEqualTo("%PDF");
    }

    @Test
    @DisplayName("generateInvoicePdf should work when invoice has no notes")
    void generateInvoicePdf_shouldHandleInvoiceWithoutNotes() {
        // Build an invoice with null notes (the notes section should simply be omitted)
        Invoice invoiceNoNotes = new Invoice(
                "INV-2024-002",
                "Beta LLC",
                "contact@beta.com",
                new BigDecimal("500.00"),
                "EUR",
                LocalDate.of(2024, 7, 1),
                null  // no notes
        );
        List<InvoiceLineItem> lineItems = List.of(
                new InvoiceLineItem(invoiceNoNotes, "Hosting Fee", 1,
                        new BigDecimal("500.00"))
        );

        byte[] result = pdfGeneratorService.generateInvoicePdf(invoiceNoNotes, lineItems);

        assertThat(result).isNotNull().isNotEmpty();
        assertThat(new String(result, 0, 4)).isEqualTo("%PDF");
    }

    @Test
    @DisplayName("generateInvoicePdf should work when invoice has empty notes")
    void generateInvoicePdf_shouldHandleInvoiceWithEmptyNotes() {
        Invoice invoiceEmptyNotes = new Invoice(
                "INV-2024-003",
                "Gamma Inc",
                "info@gamma.com",
                new BigDecimal("250.00"),
                "USD",
                LocalDate.of(2024, 8, 10),
                ""  // blank notes – the notes section should be suppressed
        );
        List<InvoiceLineItem> lineItems = List.of(
                new InvoiceLineItem(invoiceEmptyNotes, "Support Package", 1,
                        new BigDecimal("250.00"))
        );

        byte[] result = pdfGeneratorService.generateInvoicePdf(invoiceEmptyNotes, lineItems);

        assertThat(result).isNotNull().isNotEmpty();
        assertThat(new String(result, 0, 4)).isEqualTo("%PDF");
    }

    @Test
    @DisplayName("generateInvoicePdf should work with empty line items list")
    void generateInvoicePdf_shouldHandleEmptyLineItems() {
        // Edge case: no line items – the table will be rendered with just the header row
        byte[] result = pdfGeneratorService.generateInvoicePdf(sampleInvoice, List.of());

        assertThat(result).isNotNull().isNotEmpty();
        assertThat(new String(result, 0, 4)).isEqualTo("%PDF");
    }

    // ── generateInvoiceReportPdf tests ────────────────────────────────────────

    @Test
    @DisplayName("generateInvoiceReportPdf should return a valid PDF for an empty invoice list")
    void generateInvoiceReportPdf_shouldHandleEmptyInvoiceList() {
        byte[] result = pdfGeneratorService.generateInvoiceReportPdf(List.of());

        assertThat(result).isNotNull().isNotEmpty();
        assertThat(new String(result, 0, 4)).isEqualTo("%PDF");
    }

    @Test
    @DisplayName("generateInvoiceReportPdf should return a valid PDF for multiple invoices")
    void generateInvoiceReportPdf_shouldHandleMultipleInvoices() {
        // Build several distinct invoice fixtures
        Invoice inv1 = new Invoice("INV-001", "Alpha Co", "a@alpha.com",
                new BigDecimal("1000.00"), "USD", LocalDate.of(2024, 1, 10), null);
        Invoice inv2 = new Invoice("INV-002", "Beta Ltd", "b@beta.com",
                new BigDecimal("2500.50"), "EUR", LocalDate.of(2024, 2, 15), "Net 15");
        Invoice inv3 = new Invoice("INV-003", "Gamma Inc", "c@gamma.com",
                new BigDecimal("750.00"), "USD", LocalDate.of(2024, 3, 20), null);

        byte[] result = pdfGeneratorService.generateInvoiceReportPdf(List.of(inv1, inv2, inv3));

        assertThat(result).isNotNull().isNotEmpty();
        assertThat(result.length).isGreaterThan(100);
        assertThat(new String(result, 0, 4)).isEqualTo("%PDF");
    }

    // ── InvoiceLineItem domain logic tests ────────────────────────────────────

    @Test
    @DisplayName("InvoiceLineItem.getSubtotal should return quantity × unitPrice")
    void lineItem_getSubtotal_shouldReturnCorrectValue() {
        InvoiceLineItem item = new InvoiceLineItem(
                sampleInvoice, "API Integration", 5, new BigDecimal("200.00"));

        // 5 × 200.00 = 1000.00
        assertThat(item.getSubtotal()).isEqualByComparingTo(new BigDecimal("1000.00"));
    }

    @Test
    @DisplayName("InvoiceLineItem.getSubtotal should handle fractional unit prices")
    void lineItem_getSubtotal_shouldHandleFractionalPrices() {
        InvoiceLineItem item = new InvoiceLineItem(
                sampleInvoice, "Support Hour", 3, new BigDecimal("33.33"));

        // 3 × 33.33 = 99.99
        assertThat(item.getSubtotal()).isEqualByComparingTo(new BigDecimal("99.99"));
    }

    @Test
    @DisplayName("InvoiceLineItem.getSubtotal should return unitPrice for quantity of 1")
    void lineItem_getSubtotal_shouldReturnUnitPriceForQuantityOne() {
        BigDecimal price = new BigDecimal("499.99");
        InvoiceLineItem item = new InvoiceLineItem(
                sampleInvoice, "Software License", 1, price);

        assertThat(item.getSubtotal()).isEqualByComparingTo(price);
    }
}
