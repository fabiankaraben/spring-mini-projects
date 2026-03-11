package com.example.pdfgeneration.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * DTO returned by the API after an invoice is successfully created.
 *
 * <p>Contains the persisted invoice's metadata (id, number, customer, amounts)
 * along with a convenient download URL so the client can immediately fetch the PDF.
 *
 * <p>Example JSON response:
 * <pre>{@code
 * {
 *   "id": 1,
 *   "invoiceNumber": "INV-2024-001",
 *   "customerName": "Acme Corp",
 *   "customerEmail": "billing@acme.com",
 *   "totalAmount": 750.00,
 *   "currency": "USD",
 *   "issueDate": "2024-06-15",
 *   "downloadUrl": "/api/invoices/1/pdf"
 * }
 * }</pre>
 */
public class InvoiceResponse {

    /** The database-generated surrogate key of the newly created invoice. */
    private Long id;

    /** The invoice number as submitted in the request. */
    private String invoiceNumber;

    /** Customer name as submitted in the request. */
    private String customerName;

    /** Customer email as submitted in the request. */
    private String customerEmail;

    /**
     * Total amount of all line items (sum of quantity × unitPrice for each item).
     * Calculated by the service and echoed back for convenience.
     */
    private BigDecimal totalAmount;

    /** Currency code (e.g. "USD"). */
    private String currency;

    /** Issue date of the invoice. */
    private LocalDate issueDate;

    /**
     * Relative URL the client can use to download the generated PDF.
     * Example: {@code /api/invoices/1/pdf}.
     */
    private String downloadUrl;

    // ── Constructor ───────────────────────────────────────────────────────────

    public InvoiceResponse(Long id, String invoiceNumber, String customerName,
                           String customerEmail, BigDecimal totalAmount, String currency,
                           LocalDate issueDate, String downloadUrl) {
        this.id = id;
        this.invoiceNumber = invoiceNumber;
        this.customerName = customerName;
        this.customerEmail = customerEmail;
        this.totalAmount = totalAmount;
        this.currency = currency;
        this.issueDate = issueDate;
        this.downloadUrl = downloadUrl;
    }

    // ── Getters ───────────────────────────────────────────────────────────────

    public Long getId() { return id; }
    public String getInvoiceNumber() { return invoiceNumber; }
    public String getCustomerName() { return customerName; }
    public String getCustomerEmail() { return customerEmail; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public String getCurrency() { return currency; }
    public LocalDate getIssueDate() { return issueDate; }
    public String getDownloadUrl() { return downloadUrl; }
}
