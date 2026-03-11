package com.example.pdfgeneration.controller;

import com.example.pdfgeneration.dto.InvoiceRequest;
import com.example.pdfgeneration.dto.InvoiceResponse;
import com.example.pdfgeneration.service.InvoiceService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller that exposes the invoice and PDF generation API.
 *
 * <p>Base path: {@code /api/invoices}
 *
 * <h2>Endpoints</h2>
 * <ul>
 *   <li>{@code POST /api/invoices} – create a new invoice and persist it.</li>
 *   <li>{@code GET  /api/invoices} – list all invoices (metadata only).</li>
 *   <li>{@code GET  /api/invoices/{id}} – get a single invoice by id.</li>
 *   <li>{@code GET  /api/invoices/{id}/pdf} – download the invoice as a PDF.</li>
 *   <li>{@code GET  /api/invoices/report/pdf} – download a summary report PDF.</li>
 * </ul>
 *
 * <p>{@code @RestController} combines {@code @Controller} and
 * {@code @ResponseBody}, so every method's return value is automatically
 * serialised to JSON (or returned as bytes for PDF endpoints).
 */
@RestController
@RequestMapping("/api/invoices")
public class InvoiceController {

    private final InvoiceService invoiceService;

    /**
     * Constructor injection ensures the controller can be unit-tested without
     * starting a Spring context.
     *
     * @param invoiceService the service handling invoice business logic
     */
    public InvoiceController(InvoiceService invoiceService) {
        this.invoiceService = invoiceService;
    }

    // ── POST /api/invoices ────────────────────────────────────────────────────

    /**
     * Creates a new invoice from the JSON request body.
     *
     * <p>{@code @Valid} triggers Bean Validation on {@link InvoiceRequest} before
     * the method body executes. If any constraint fails, Spring automatically
     * returns {@code 400 Bad Request} with validation error details.
     *
     * @param request the validated invoice creation request
     * @return {@code 201 Created} with the created invoice metadata in the body
     */
    @PostMapping
    public ResponseEntity<InvoiceResponse> createInvoice(
            @RequestBody @Valid InvoiceRequest request) {
        InvoiceResponse response = invoiceService.createInvoice(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ── GET /api/invoices ─────────────────────────────────────────────────────

    /**
     * Returns metadata for all invoices stored in the database.
     *
     * @return {@code 200 OK} with a JSON array of invoice response DTOs
     */
    @GetMapping
    public ResponseEntity<List<InvoiceResponse>> getAllInvoices() {
        return ResponseEntity.ok(invoiceService.getAllInvoices());
    }

    // ── GET /api/invoices/{id} ────────────────────────────────────────────────

    /**
     * Returns metadata for a single invoice.
     *
     * @param id the surrogate key of the invoice
     * @return {@code 200 OK} with the invoice response DTO
     */
    @GetMapping("/{id}")
    public ResponseEntity<InvoiceResponse> getInvoice(@PathVariable Long id) {
        return ResponseEntity.ok(invoiceService.getInvoice(id));
    }

    // ── GET /api/invoices/{id}/pdf ────────────────────────────────────────────

    /**
     * Generates and streams an invoice PDF for the given invoice id.
     *
     * <h2>Response headers</h2>
     * <ul>
     *   <li>{@code Content-Type: application/pdf} – tells browsers/HTTP clients
     *       that the body is a PDF document.</li>
     *   <li>{@code Content-Disposition: attachment; filename="invoice-{id}.pdf"} –
     *       instructs the browser to download the file rather than render it inline.
     *       Change to {@code inline} if you want to open the PDF in the browser tab.</li>
     * </ul>
     *
     * @param id the surrogate key of the invoice
     * @return {@code 200 OK} with the raw PDF bytes as the response body
     */
    @GetMapping(value = "/{id}/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> downloadInvoicePdf(@PathVariable Long id) {
        byte[] pdfBytes = invoiceService.generateInvoicePdf(id);

        // Build Content-Disposition header so the browser treats it as a file download
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"invoice-" + id + ".pdf\"");
        headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE);

        return ResponseEntity.ok()
                .headers(headers)
                .body(pdfBytes);
    }

    // ── GET /api/invoices/report/pdf ──────────────────────────────────────────

    /**
     * Generates and streams a summary report PDF listing all invoices.
     *
     * <p>The report is always generated fresh from the current database state,
     * so it reflects any invoices added since the last request.
     *
     * @return {@code 200 OK} with the raw PDF bytes of the report
     */
    @GetMapping(value = "/report/pdf", produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> downloadReportPdf() {
        byte[] pdfBytes = invoiceService.generateReportPdf();

        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"invoice-report.pdf\"");
        headers.add(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_PDF_VALUE);

        return ResponseEntity.ok()
                .headers(headers)
                .body(pdfBytes);
    }
}
