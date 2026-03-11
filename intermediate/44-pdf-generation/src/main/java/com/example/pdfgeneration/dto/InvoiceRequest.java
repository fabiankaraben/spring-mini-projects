package com.example.pdfgeneration.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDate;
import java.util.List;

/**
 * DTO carrying all data needed to create an invoice and generate its PDF.
 *
 * <p>Clients send this as a JSON body to {@code POST /api/invoices}.
 * Spring's {@code @Valid} on the controller method triggers Bean Validation
 * before the request reaches the service layer.
 *
 * <p>Example JSON:
 * <pre>{@code
 * {
 *   "invoiceNumber": "INV-2024-001",
 *   "customerName": "Acme Corp",
 *   "customerEmail": "billing@acme.com",
 *   "issueDate": "2024-06-15",
 *   "currency": "USD",
 *   "notes": "Payment due in 30 days.",
 *   "lineItems": [
 *     { "description": "Consulting", "quantity": 5, "unitPrice": 150.00 }
 *   ]
 * }
 * }</pre>
 */
public class InvoiceRequest {

    /** Unique invoice identifier printed prominently in the PDF header. */
    @NotBlank(message = "Invoice number must not be blank")
    private String invoiceNumber;

    /** Full name of the billed customer. */
    @NotBlank(message = "Customer name must not be blank")
    private String customerName;

    /** Customer email address included in the PDF. */
    @NotBlank(message = "Customer email must not be blank")
    @Email(message = "Customer email must be a valid email address")
    private String customerEmail;

    /**
     * Date the invoice was issued.
     * Serialised/deserialised as an ISO-8601 date string (e.g. "2024-06-15").
     */
    @NotNull(message = "Issue date must not be null")
    private LocalDate issueDate;

    /** ISO 4217 currency code, e.g. "USD", "EUR". Defaults to "USD". */
    private String currency = "USD";

    /** Optional footer notes (payment terms, bank details, etc.). */
    private String notes;

    /**
     * At least one line item is required; each item is also validated via
     * {@code @Valid} which cascades Bean Validation to {@link InvoiceLineItemRequest}.
     */
    @NotEmpty(message = "At least one line item is required")
    @Valid
    private List<InvoiceLineItemRequest> lineItems;

    // ── Default constructor (required by Jackson for deserialization) ──────────

    public InvoiceRequest() {
    }

    // ── Getters and setters ───────────────────────────────────────────────────

    public String getInvoiceNumber() { return invoiceNumber; }
    public void setInvoiceNumber(String invoiceNumber) { this.invoiceNumber = invoiceNumber; }

    public String getCustomerName() { return customerName; }
    public void setCustomerName(String customerName) { this.customerName = customerName; }

    public String getCustomerEmail() { return customerEmail; }
    public void setCustomerEmail(String customerEmail) { this.customerEmail = customerEmail; }

    public LocalDate getIssueDate() { return issueDate; }
    public void setIssueDate(LocalDate issueDate) { this.issueDate = issueDate; }

    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public List<InvoiceLineItemRequest> getLineItems() { return lineItems; }
    public void setLineItems(List<InvoiceLineItemRequest> lineItems) { this.lineItems = lineItems; }
}
