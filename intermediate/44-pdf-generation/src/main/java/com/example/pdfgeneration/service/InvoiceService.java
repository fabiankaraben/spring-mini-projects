package com.example.pdfgeneration.service;

import com.example.pdfgeneration.domain.Invoice;
import com.example.pdfgeneration.domain.InvoiceLineItem;
import com.example.pdfgeneration.dto.InvoiceRequest;
import com.example.pdfgeneration.dto.InvoiceResponse;
import com.example.pdfgeneration.repository.InvoiceLineItemRepository;
import com.example.pdfgeneration.repository.InvoiceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.NoSuchElementException;

/**
 * Application service that orchestrates invoice creation and PDF generation.
 *
 * <p>This service is the bridge between the HTTP layer (controllers) and the
 * infrastructure layer (repositories, {@link PdfGeneratorService}).
 *
 * <h2>Responsibilities</h2>
 * <ul>
 *   <li>Validate business rules (e.g. duplicate invoice number detection).</li>
 *   <li>Persist new {@link Invoice} and {@link InvoiceLineItem} records.</li>
 *   <li>Delegate PDF byte-array generation to {@link PdfGeneratorService}.</li>
 *   <li>Build {@link InvoiceResponse} DTOs for the controller.</li>
 * </ul>
 *
 * <p>{@code @Transactional} on {@link #createInvoice} ensures that the invoice
 * and all its line items are persisted atomically – if saving any line item
 * fails the whole transaction is rolled back.
 */
@Service
public class InvoiceService {

    private final InvoiceRepository invoiceRepository;
    private final InvoiceLineItemRepository lineItemRepository;
    private final PdfGeneratorService pdfGeneratorService;

    /**
     * Constructor injection (preferred over field injection for testability).
     *
     * @param invoiceRepository    repository for {@link Invoice} entities
     * @param lineItemRepository   repository for {@link InvoiceLineItem} entities
     * @param pdfGeneratorService  service that converts domain objects to PDF bytes
     */
    public InvoiceService(InvoiceRepository invoiceRepository,
                          InvoiceLineItemRepository lineItemRepository,
                          PdfGeneratorService pdfGeneratorService) {
        this.invoiceRepository = invoiceRepository;
        this.lineItemRepository = lineItemRepository;
        this.pdfGeneratorService = pdfGeneratorService;
    }

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Creates a new invoice from the given request and persists it to the database.
     *
     * <h2>Steps performed</h2>
     * <ol>
     *   <li>Check that the invoice number is not already taken.</li>
     *   <li>Compute the total amount by summing all line-item subtotals.</li>
     *   <li>Persist the parent {@link Invoice} entity.</li>
     *   <li>Persist each {@link InvoiceLineItem} associated with the invoice.</li>
     *   <li>Return an {@link InvoiceResponse} with a PDF download URL.</li>
     * </ol>
     *
     * @param request validated DTO from the controller
     * @return a response DTO with invoice metadata and a PDF download URL
     * @throws IllegalArgumentException if the invoice number is already taken
     */
    @Transactional
    public InvoiceResponse createInvoice(InvoiceRequest request) {
        // ── Business rule: invoice numbers must be unique ─────────────────────
        if (invoiceRepository.existsByInvoiceNumber(request.getInvoiceNumber())) {
            throw new IllegalArgumentException(
                    "Invoice number '" + request.getInvoiceNumber() + "' already exists");
        }

        // ── Compute total amount from line items ──────────────────────────────
        BigDecimal total = request.getLineItems().stream()
                .map(item -> item.getUnitPrice().multiply(BigDecimal.valueOf(item.getQuantity())))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // ── Persist the invoice entity ────────────────────────────────────────
        Invoice invoice = new Invoice(
                request.getInvoiceNumber(),
                request.getCustomerName(),
                request.getCustomerEmail(),
                total,
                request.getCurrency() != null ? request.getCurrency() : "USD",
                request.getIssueDate(),
                request.getNotes()
        );
        Invoice saved = invoiceRepository.save(invoice);

        // ── Persist each line item linked to the saved invoice ────────────────
        request.getLineItems().forEach(itemReq -> {
            InvoiceLineItem lineItem = new InvoiceLineItem(
                    saved,
                    itemReq.getDescription(),
                    itemReq.getQuantity(),
                    itemReq.getUnitPrice()
            );
            lineItemRepository.save(lineItem);
        });

        // ── Build and return the response DTO ─────────────────────────────────
        return toResponse(saved);
    }

    /**
     * Generates a PDF byte array for the invoice with the given id.
     *
     * <p>The PDF is generated on-the-fly from the stored invoice and its line
     * items; no PDF binary is stored in the database.
     *
     * @param invoiceId the surrogate key of the invoice
     * @return the raw PDF bytes to stream back to the client
     * @throws NoSuchElementException if no invoice with that id exists
     */
    @Transactional(readOnly = true)
    public byte[] generateInvoicePdf(Long invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new NoSuchElementException(
                        "Invoice not found with id: " + invoiceId));

        List<InvoiceLineItem> lineItems = lineItemRepository.findByInvoiceId(invoiceId);

        return pdfGeneratorService.generateInvoicePdf(invoice, lineItems);
    }

    /**
     * Generates a summary report PDF listing all invoices in the system.
     *
     * @return the raw PDF bytes for the summary report
     */
    @Transactional(readOnly = true)
    public byte[] generateReportPdf() {
        List<Invoice> invoices = invoiceRepository.findAll();
        return pdfGeneratorService.generateInvoiceReportPdf(invoices);
    }

    /**
     * Returns metadata for all invoices stored in the database.
     *
     * @return list of response DTOs (may be empty, never null)
     */
    @Transactional(readOnly = true)
    public List<InvoiceResponse> getAllInvoices() {
        return invoiceRepository.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    /**
     * Returns metadata for the invoice with the given id.
     *
     * @param invoiceId the surrogate key
     * @return the response DTO
     * @throws NoSuchElementException if no invoice with that id exists
     */
    @Transactional(readOnly = true)
    public InvoiceResponse getInvoice(Long invoiceId) {
        Invoice invoice = invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> new NoSuchElementException(
                        "Invoice not found with id: " + invoiceId));
        return toResponse(invoice);
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Converts an {@link Invoice} entity to an {@link InvoiceResponse} DTO.
     *
     * <p>The download URL follows the pattern {@code /api/invoices/{id}/pdf}
     * so the client can construct the full URL from the response.
     *
     * @param invoice the entity to convert
     * @return the corresponding DTO
     */
    private InvoiceResponse toResponse(Invoice invoice) {
        return new InvoiceResponse(
                invoice.getId(),
                invoice.getInvoiceNumber(),
                invoice.getCustomerName(),
                invoice.getCustomerEmail(),
                invoice.getTotalAmount(),
                invoice.getCurrency(),
                invoice.getIssueDate(),
                "/api/invoices/" + invoice.getId() + "/pdf"
        );
    }
}
