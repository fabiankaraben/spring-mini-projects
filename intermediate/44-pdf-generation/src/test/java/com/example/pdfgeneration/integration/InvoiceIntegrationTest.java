package com.example.pdfgeneration.integration;

import com.example.pdfgeneration.dto.InvoiceLineItemRequest;
import com.example.pdfgeneration.dto.InvoiceRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Full integration tests for the Invoice / PDF Generation API.
 *
 * <h2>What these tests cover</h2>
 * <ul>
 *   <li>Invoice creation ({@code POST /api/invoices}) – happy path and error cases.</li>
 *   <li>Invoice listing ({@code GET /api/invoices}) – empty list and populated list.</li>
 *   <li>Single invoice lookup ({@code GET /api/invoices/{id}}) – found and not found.</li>
 *   <li>Invoice PDF download ({@code GET /api/invoices/{id}/pdf}) – valid PDF bytes.</li>
 *   <li>Report PDF download ({@code GET /api/invoices/report/pdf}) – valid PDF bytes.</li>
 *   <li>Validation failures – blank fields, missing items, bad email.</li>
 *   <li>Business rule enforcement – duplicate invoice number.</li>
 * </ul>
 *
 * <h2>Infrastructure</h2>
 * <ul>
 *   <li><strong>Testcontainers</strong> – spins up a real PostgreSQL 16 Docker
 *       container for the duration of this test class. Gives us confidence that
 *       the JPA layer, the SQL dialect, and the schema creation all work correctly
 *       against a real database.</li>
 *   <li><strong>{@code @DynamicPropertySource}</strong> – injects the dynamic host/port
 *       assigned by Testcontainers into Spring's DataSource configuration so that
 *       the application connects to the containerised database.</li>
 *   <li><strong>MockMvc</strong> – sends HTTP requests through the full Spring MVC
 *       stack (filters, controllers, serialisation) without starting a real TCP server.</li>
 *   <li><strong>{@code @ActiveProfiles("test")}</strong> – activates
 *       {@code application-test.yml} which sets {@code ddl-auto: create-drop} to
 *       give every test class a fresh schema.</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
@ActiveProfiles("test")
@DisplayName("Invoice API – Integration Tests")
class InvoiceIntegrationTest {

    /**
     * PostgreSQL Testcontainer shared across all test methods in this class.
     *
     * <p>Declaring the field {@code static} ensures the container is started once
     * before the Spring {@code ApplicationContext} is created. Without {@code static},
     * the container would start after Spring tries to connect to a database that does
     * not yet exist.
     */
    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("pdfdb_test")
            .withUsername("testuser")
            .withPassword("testpass");

    /**
     * Overrides Spring DataSource properties with the values assigned by
     * Testcontainers at runtime (host, port, db name, credentials).
     *
     * <p>This method runs after the container starts but before Spring creates
     * the {@code ApplicationContext}, so the datasource URL is already correct
     * when Hibernate tries to connect.
     *
     * @param registry the dynamic property registry provided by Spring Test
     */
    @DynamicPropertySource
    static void overrideDataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    /**
     * Explicitly starts the container before any test runs to guarantee it is
     * fully ready when {@link #overrideDataSourceProperties} is called.
     */
    @BeforeAll
    static void beforeAll() {
        postgres.start();
    }

    /**
     * Stops the container after all tests finish, releasing Docker resources promptly.
     */
    @AfterAll
    static void afterAll() {
        postgres.stop();
    }

    @Autowired
    private MockMvc mockMvc;

    /** Jackson mapper for serialising request DTOs to JSON. */
    @Autowired
    private ObjectMapper objectMapper;

    // ── POST /api/invoices ────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/invoices should return 201 and invoice metadata for a valid request")
    void createInvoice_shouldReturn201WithMetadata() throws Exception {
        InvoiceRequest request = buildValidRequest("INT-INV-001", "Acme Corp", "billing@acme.com");

        mockMvc.perform(post("/api/invoices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.invoiceNumber", is("INT-INV-001")))
                .andExpect(jsonPath("$.customerName", is("Acme Corp")))
                .andExpect(jsonPath("$.customerEmail", is("billing@acme.com")))
                .andExpect(jsonPath("$.currency", is("USD")))
                // Total = 5 × 150.00 = 750.00
                .andExpect(jsonPath("$.totalAmount", is(750.00)))
                // Download URL must match /api/invoices/{id}/pdf pattern
                .andExpect(jsonPath("$.downloadUrl", matchesPattern("/api/invoices/\\d+/pdf")));
    }

    @Test
    @DisplayName("POST /api/invoices should return 400 for blank invoice number")
    void createInvoice_shouldReturn400ForBlankInvoiceNumber() throws Exception {
        InvoiceRequest request = buildValidRequest("", "Acme Corp", "billing@acme.com");

        mockMvc.perform(post("/api/invoices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.invoiceNumber", notNullValue()));
    }

    @Test
    @DisplayName("POST /api/invoices should return 400 for invalid customer email")
    void createInvoice_shouldReturn400ForInvalidEmail() throws Exception {
        InvoiceRequest request = buildValidRequest("INT-INV-EMAIL", "Acme Corp", "not-an-email");

        mockMvc.perform(post("/api/invoices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.customerEmail", notNullValue()));
    }

    @Test
    @DisplayName("POST /api/invoices should return 400 when line items list is empty")
    void createInvoice_shouldReturn400ForEmptyLineItems() throws Exception {
        InvoiceRequest request = buildValidRequest("INT-INV-EMPTY", "Acme Corp", "billing@acme.com");
        request.setLineItems(List.of()); // override with empty list

        mockMvc.perform(post("/api/invoices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /api/invoices should return 400 for duplicate invoice number")
    void createInvoice_shouldReturn400ForDuplicateInvoiceNumber() throws Exception {
        InvoiceRequest request = buildValidRequest("INT-INV-DUP", "Acme Corp", "billing@acme.com");

        // First request must succeed
        mockMvc.perform(post("/api/invoices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Second request with the same invoice number must fail
        mockMvc.perform(post("/api/invoices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message", containsString("INT-INV-DUP")));
    }

    @Test
    @DisplayName("POST /api/invoices should return 400 for line item with zero quantity")
    void createInvoice_shouldReturn400ForZeroQuantity() throws Exception {
        InvoiceRequest request = buildValidRequest("INT-INV-ZERO-QTY", "Beta Corp", "beta@corp.com");
        request.setLineItems(List.of(
                new InvoiceLineItemRequest("Invalid Item", 0, new BigDecimal("100.00"))
        ));

        mockMvc.perform(post("/api/invoices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    // ── GET /api/invoices ─────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/invoices should return 200 and a list of invoices")
    void getAllInvoices_shouldReturn200WithList() throws Exception {
        // Create an invoice first
        InvoiceRequest request = buildValidRequest("INT-INV-LIST", "List Corp", "list@corp.com");
        mockMvc.perform(post("/api/invoices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Now retrieve all invoices
        mockMvc.perform(get("/api/invoices"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", isA(java.util.List.class)))
                .andExpect(jsonPath("$.length()", greaterThan(0)));
    }

    // ── GET /api/invoices/{id} ────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/invoices/{id} should return 200 with invoice metadata")
    void getInvoice_shouldReturn200ForExistingInvoice() throws Exception {
        // Create the invoice and parse the returned id
        InvoiceRequest request = buildValidRequest("INT-INV-GET", "Get Corp", "get@corp.com");
        MvcResult createResult = mockMvc.perform(post("/api/invoices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        // Extract the id from the JSON response
        String responseBody = createResult.getResponse().getContentAsString();
        Long id = objectMapper.readTree(responseBody).get("id").asLong();

        // Fetch by id and verify
        mockMvc.perform(get("/api/invoices/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(id.intValue())))
                .andExpect(jsonPath("$.invoiceNumber", is("INT-INV-GET")));
    }

    @Test
    @DisplayName("GET /api/invoices/{id} should return 404 for unknown id")
    void getInvoice_shouldReturn404ForUnknownId() throws Exception {
        mockMvc.perform(get("/api/invoices/99999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error", is("Not Found")));
    }

    // ── GET /api/invoices/{id}/pdf ────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/invoices/{id}/pdf should return 200 with valid PDF bytes")
    void downloadInvoicePdf_shouldReturn200WithPdfBytes() throws Exception {
        // Create the invoice first
        InvoiceRequest request = buildValidRequest("INT-INV-PDF", "PDF Corp", "pdf@corp.com");
        MvcResult createResult = mockMvc.perform(post("/api/invoices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        Long id = objectMapper.readTree(
                createResult.getResponse().getContentAsString()).get("id").asLong();

        // Request the PDF
        MvcResult pdfResult = mockMvc.perform(get("/api/invoices/" + id + "/pdf"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PDF_VALUE))
                .andExpect(header().string("Content-Disposition",
                        containsString("attachment")))
                .andReturn();

        byte[] pdfBytes = pdfResult.getResponse().getContentAsByteArray();

        // Verify the PDF magic bytes: %PDF
        assertThat(pdfBytes).isNotEmpty();
        assertThat(new String(pdfBytes, 0, 4)).isEqualTo("%PDF");
    }

    @Test
    @DisplayName("GET /api/invoices/{id}/pdf should return 404 for unknown invoice id")
    void downloadInvoicePdf_shouldReturn404ForUnknownId() throws Exception {
        mockMvc.perform(get("/api/invoices/88888/pdf"))
                .andExpect(status().isNotFound());
    }

    // ── GET /api/invoices/report/pdf ──────────────────────────────────────────

    @Test
    @DisplayName("GET /api/invoices/report/pdf should return 200 with valid PDF bytes")
    void downloadReportPdf_shouldReturn200WithPdfBytes() throws Exception {
        // Create at least one invoice to populate the report
        InvoiceRequest request = buildValidRequest("INT-INV-REPORT", "Report Corp", "report@corp.com");
        mockMvc.perform(post("/api/invoices")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated());

        // Download the report
        MvcResult reportResult = mockMvc.perform(get("/api/invoices/report/pdf"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", MediaType.APPLICATION_PDF_VALUE))
                .andExpect(header().string("Content-Disposition",
                        containsString("invoice-report.pdf")))
                .andReturn();

        byte[] pdfBytes = reportResult.getResponse().getContentAsByteArray();

        assertThat(pdfBytes).isNotEmpty();
        assertThat(new String(pdfBytes, 0, 4)).isEqualTo("%PDF");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Builds a valid {@link InvoiceRequest} fixture with one line item.
     *
     * @param invoiceNumber the unique invoice number
     * @param customerName  billing customer name
     * @param customerEmail billing customer email
     * @return a fully populated request ready to be serialised as JSON
     */
    private InvoiceRequest buildValidRequest(String invoiceNumber,
                                             String customerName,
                                             String customerEmail) {
        InvoiceLineItemRequest lineItem = new InvoiceLineItemRequest(
                "Consulting Services", 5, new BigDecimal("150.00"));

        InvoiceRequest request = new InvoiceRequest();
        request.setInvoiceNumber(invoiceNumber);
        request.setCustomerName(customerName);
        request.setCustomerEmail(customerEmail);
        request.setIssueDate(LocalDate.of(2024, 6, 15));
        request.setCurrency("USD");
        request.setNotes("Payment due in 30 days.");
        request.setLineItems(List.of(lineItem));
        return request;
    }
}
