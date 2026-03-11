package com.example.excelexport.controller;

import com.example.excelexport.model.ExcelReportRequest;
import com.example.excelexport.model.Product;
import com.example.excelexport.service.ExcelExportService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

/**
 * REST controller exposing endpoints for generating and downloading Excel reports.
 *
 * <p>Two endpoints are provided:
 * <ul>
 *   <li><b>GET /api/export/products/sample</b>
 *       – returns a pre-built sample workbook so you can try the feature
 *         without constructing a request body.</li>
 *   <li><b>POST /api/export/products</b>
 *       – accepts a JSON body ({@link ExcelReportRequest}) and returns a
 *         customised workbook for the supplied products.</li>
 * </ul>
 *
 * <p>Both endpoints set the following HTTP response headers:
 * <ul>
 *   <li>{@code Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet}
 *       – the IANA media type for .xlsx files</li>
 *   <li>{@code Content-Disposition: attachment; filename="..."} – instructs the
 *       browser to download the response as a file rather than display it inline</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/export")
public class ExcelExportController {

    /**
     * IANA media type for OOXML spreadsheet (.xlsx).
     * Spring does not define a constant for this type, so we define one here.
     */
    private static final String EXCEL_MEDIA_TYPE =
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";

    private final ExcelExportService excelExportService;

    /**
     * Constructor injection – Spring Boot auto-wires the service bean.
     * Constructor injection is preferred over field injection because it makes
     * dependencies explicit and allows the class to be used in unit tests without
     * a Spring context.
     */
    public ExcelExportController(ExcelExportService excelExportService) {
        this.excelExportService = excelExportService;
    }

    /**
     * GET /api/export/products/sample
     *
     * <p>Returns a ready-made Excel file containing a handful of demo products.
     * This endpoint is useful for quickly verifying the integration without
     * having to craft a request body.
     *
     * @return 200 OK with the .xlsx binary as the response body
     * @throws IOException if the workbook serialisation fails (propagated as 500)
     */
    @GetMapping("/products/sample")
    public ResponseEntity<byte[]> downloadSampleReport() throws IOException {
        // Build a fixed list of sample products for demonstration purposes.
        List<Product> sampleProducts = List.of(
                new Product("PRD-001", "Laptop Pro 15",    "Electronics",  new BigDecimal("1299.99"), 45),
                new Product("PRD-002", "Wireless Mouse",   "Electronics",  new BigDecimal("29.95"),   200),
                new Product("PRD-003", "USB-C Hub 7-port", "Electronics",  new BigDecimal("49.99"),   150),
                new Product("PRD-004", "Standing Desk",    "Furniture",    new BigDecimal("549.00"),  20),
                new Product("PRD-005", "Ergonomic Chair",  "Furniture",    new BigDecimal("399.00"),  35),
                new Product("PRD-006", "Java in Action",   "Books",        new BigDecimal("59.90"),   80),
                new Product("PRD-007", "Spring Boot Guide","Books",        new BigDecimal("44.95"),   60),
                new Product("PRD-008", "Notebook A5",      "Stationery",   new BigDecimal("4.99"),    500),
                new Product("PRD-009", "Ballpoint Pen 12pk","Stationery",  new BigDecimal("3.49"),    750),
                new Product("PRD-010", "Coffee Mug 350ml", "Kitchen",      new BigDecimal("14.99"),   120)
        );

        // Delegate workbook generation to the service layer.
        byte[] excelBytes = excelExportService.generateProductReport("Sample Product Inventory", sampleProducts);

        // Build the HTTP response with appropriate headers.
        return buildExcelResponse(excelBytes, "sample-products.xlsx");
    }

    /**
     * POST /api/export/products
     *
     * <p>Accepts a JSON request body containing a report title and a list of
     * products, then returns a customised Excel workbook.
     *
     * <p>{@code @Valid} triggers Bean Validation on the request body and its
     * nested {@link Product} objects. If validation fails, Spring returns 400.
     *
     * @param request validated request payload
     * @return 200 OK with the .xlsx binary as the response body
     * @throws IOException if the workbook serialisation fails (propagated as 500)
     */
    @PostMapping("/products")
    public ResponseEntity<byte[]> downloadCustomReport(
            @Valid @RequestBody ExcelReportRequest request) throws IOException {

        // Generate the workbook using the title and products from the request.
        byte[] excelBytes = excelExportService.generateProductReport(
                request.reportTitle(), request.products());

        // Derive a file-system-safe filename from the report title:
        // replace spaces with hyphens, lower-case, and append the extension.
        String filename = request.reportTitle()
                .toLowerCase()
                .replaceAll("\\s+", "-")
                .replaceAll("[^a-z0-9\\-]", "")
                + ".xlsx";

        return buildExcelResponse(excelBytes, filename);
    }

    /**
     * Builds a {@link ResponseEntity} carrying the Excel bytes with the correct
     * {@code Content-Type} and {@code Content-Disposition} headers.
     *
     * @param excelBytes serialised .xlsx workbook
     * @param filename   name that the browser will use when saving the file
     */
    private ResponseEntity<byte[]> buildExcelResponse(byte[] excelBytes, String filename) {
        return ResponseEntity.ok()
                // Tell the HTTP client this is an .xlsx file.
                .contentType(MediaType.parseMediaType(EXCEL_MEDIA_TYPE))
                // "attachment" triggers a Save-As dialog in browsers;
                // "inline" would attempt to display the file in the browser.
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"")
                // Explicitly set Content-Length so the browser can display
                // a meaningful download progress bar.
                .contentLength(excelBytes.length)
                .body(excelBytes);
    }
}
