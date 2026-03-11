package com.example.pdfgeneration.unit;

import com.example.pdfgeneration.domain.Invoice;
import com.example.pdfgeneration.domain.InvoiceLineItem;
import com.example.pdfgeneration.dto.InvoiceLineItemRequest;
import com.example.pdfgeneration.dto.InvoiceRequest;
import com.example.pdfgeneration.dto.InvoiceResponse;
import com.example.pdfgeneration.repository.InvoiceLineItemRepository;
import com.example.pdfgeneration.repository.InvoiceRepository;
import com.example.pdfgeneration.service.InvoiceService;
import com.example.pdfgeneration.service.PdfGeneratorService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.NoSuchElementException;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link InvoiceService}.
 *
 * <p>These tests verify the service-layer business logic in complete isolation:
 * no Spring context, no database, no Docker containers.
 * All dependencies are replaced with Mockito mocks so we can control exactly
 * what each collaborator returns.
 *
 * <h2>Mockito annotations used</h2>
 * <ul>
 *   <li>{@code @ExtendWith(MockitoExtension.class)} – activates the Mockito
 *       JUnit 5 extension, which processes {@code @Mock} annotations and
 *       verifies interactions after each test.</li>
 *   <li>{@code @Mock} – creates a Mockito mock for the annotated field type and
 *       injects it into the test class before each test method runs.</li>
 * </ul>
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("InvoiceService – Unit Tests")
class InvoiceServiceTest {

    @Mock
    private InvoiceRepository invoiceRepository;

    @Mock
    private InvoiceLineItemRepository lineItemRepository;

    @Mock
    private PdfGeneratorService pdfGeneratorService;

    /**
     * The class under test. Constructed manually in {@link #setUp()} using the
     * mocks above so that no Spring context is needed.
     */
    private InvoiceService invoiceService;

    /**
     * A reusable valid {@link InvoiceRequest} fixture.
     */
    private InvoiceRequest validRequest;

    @BeforeEach
    void setUp() {
        // Build the service under test with injected mocks
        invoiceService = new InvoiceService(invoiceRepository, lineItemRepository, pdfGeneratorService);

        // Build a reusable valid request fixture
        InvoiceLineItemRequest lineItem = new InvoiceLineItemRequest(
                "Consulting Services", 5, new BigDecimal("150.00"));

        validRequest = new InvoiceRequest();
        validRequest.setInvoiceNumber("INV-2024-001");
        validRequest.setCustomerName("Acme Corporation");
        validRequest.setCustomerEmail("billing@acme.com");
        validRequest.setIssueDate(LocalDate.of(2024, 6, 15));
        validRequest.setCurrency("USD");
        validRequest.setNotes("Payment due in 30 days.");
        validRequest.setLineItems(List.of(lineItem));
    }

    // ── createInvoice tests ───────────────────────────────────────────────────

    @Test
    @DisplayName("createInvoice should return an InvoiceResponse with correct data")
    void createInvoice_shouldReturnCorrectResponse() {
        // Arrange: invoice number is not taken; save() returns a persisted entity
        when(invoiceRepository.existsByInvoiceNumber("INV-2024-001")).thenReturn(false);
        Invoice persisted = buildPersistedInvoice(1L, "INV-2024-001",
                "Acme Corporation", "billing@acme.com",
                new BigDecimal("750.00"), "USD", LocalDate.of(2024, 6, 15));
        when(invoiceRepository.save(any(Invoice.class))).thenReturn(persisted);
        when(lineItemRepository.save(any(InvoiceLineItem.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        InvoiceResponse response = invoiceService.createInvoice(validRequest);

        // Assert: all fields populated correctly
        assertThat(response.getId()).isEqualTo(1L);
        assertThat(response.getInvoiceNumber()).isEqualTo("INV-2024-001");
        assertThat(response.getCustomerName()).isEqualTo("Acme Corporation");
        assertThat(response.getCustomerEmail()).isEqualTo("billing@acme.com");
        assertThat(response.getCurrency()).isEqualTo("USD");
        // Download URL follows the convention /api/invoices/{id}/pdf
        assertThat(response.getDownloadUrl()).isEqualTo("/api/invoices/1/pdf");
    }

    @Test
    @DisplayName("createInvoice should compute total amount from line items")
    void createInvoice_shouldComputeCorrectTotalAmount() {
        // 5 × 150.00 = 750.00
        when(invoiceRepository.existsByInvoiceNumber(any())).thenReturn(false);
        Invoice persisted = buildPersistedInvoice(1L, "INV-2024-001",
                "Acme Corporation", "billing@acme.com",
                new BigDecimal("750.00"), "USD", LocalDate.of(2024, 6, 15));
        when(invoiceRepository.save(any(Invoice.class))).thenReturn(persisted);
        when(lineItemRepository.save(any(InvoiceLineItem.class))).thenAnswer(i -> i.getArgument(0));

        InvoiceResponse response = invoiceService.createInvoice(validRequest);

        // The service persists the computed total; the response should reflect it
        assertThat(response.getTotalAmount()).isEqualByComparingTo(new BigDecimal("750.00"));
    }

    @Test
    @DisplayName("createInvoice should throw IllegalArgumentException for duplicate invoice number")
    void createInvoice_shouldThrowForDuplicateInvoiceNumber() {
        // Arrange: invoice number already exists
        when(invoiceRepository.existsByInvoiceNumber("INV-2024-001")).thenReturn(true);

        // Act & Assert: the service must reject the duplicate before touching the DB
        assertThatThrownBy(() -> invoiceService.createInvoice(validRequest))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("INV-2024-001");

        // Verify that save() was never called because the check short-circuits first
        verify(invoiceRepository, never()).save(any());
    }

    @Test
    @DisplayName("createInvoice should save one line item per request item")
    void createInvoice_shouldSaveOneLineItemPerRequestItem() {
        // Add a second line item to the request
        InvoiceLineItemRequest item2 = new InvoiceLineItemRequest(
                "Infrastructure Setup", 2, new BigDecimal("500.00"));
        validRequest.setLineItems(List.of(
                validRequest.getLineItems().get(0), item2));

        when(invoiceRepository.existsByInvoiceNumber(any())).thenReturn(false);
        Invoice persisted = buildPersistedInvoice(1L, "INV-2024-001",
                "Acme Corporation", "billing@acme.com",
                new BigDecimal("1750.00"), "USD", LocalDate.of(2024, 6, 15));
        when(invoiceRepository.save(any(Invoice.class))).thenReturn(persisted);
        when(lineItemRepository.save(any(InvoiceLineItem.class))).thenAnswer(i -> i.getArgument(0));

        invoiceService.createInvoice(validRequest);

        // Verify lineItemRepository.save() was called exactly 2 times (once per item)
        verify(lineItemRepository, times(2)).save(any(InvoiceLineItem.class));
    }

    // ── generateInvoicePdf tests ──────────────────────────────────────────────

    @Test
    @DisplayName("generateInvoicePdf should return byte array from PdfGeneratorService")
    void generateInvoicePdf_shouldReturnBytesFromGenerator() {
        Invoice invoice = buildPersistedInvoice(1L, "INV-001", "Acme", "a@acme.com",
                BigDecimal.TEN, "USD", LocalDate.now());
        when(invoiceRepository.findById(1L)).thenReturn(Optional.of(invoice));
        when(lineItemRepository.findByInvoiceId(1L)).thenReturn(List.of());
        byte[] fakePdf = "%PDF-fake".getBytes();
        when(pdfGeneratorService.generateInvoicePdf(any(Invoice.class), any()))
                .thenReturn(fakePdf);

        byte[] result = invoiceService.generateInvoicePdf(1L);

        assertThat(result).isEqualTo(fakePdf);
    }

    @Test
    @DisplayName("generateInvoicePdf should throw NoSuchElementException for unknown id")
    void generateInvoicePdf_shouldThrowForUnknownId() {
        when(invoiceRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> invoiceService.generateInvoicePdf(99L))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("99");
    }

    // ── generateReportPdf tests ───────────────────────────────────────────────

    @Test
    @DisplayName("generateReportPdf should call findAll and delegate to PdfGeneratorService")
    void generateReportPdf_shouldDelegateToPdfGenerator() {
        List<Invoice> allInvoices = List.of(
                buildPersistedInvoice(1L, "INV-001", "Alpha", "a@a.com",
                        BigDecimal.TEN, "USD", LocalDate.now())
        );
        when(invoiceRepository.findAll()).thenReturn(allInvoices);
        byte[] fakeReport = "%PDF-report".getBytes();
        when(pdfGeneratorService.generateInvoiceReportPdf(allInvoices)).thenReturn(fakeReport);

        byte[] result = invoiceService.generateReportPdf();

        assertThat(result).isEqualTo(fakeReport);
        verify(pdfGeneratorService).generateInvoiceReportPdf(allInvoices);
    }

    // ── getAllInvoices tests ───────────────────────────────────────────────────

    @Test
    @DisplayName("getAllInvoices should return a list of InvoiceResponse DTOs")
    void getAllInvoices_shouldReturnMappedDtos() {
        Invoice inv = buildPersistedInvoice(5L, "INV-005", "Zeta Ltd", "z@z.com",
                new BigDecimal("300.00"), "EUR", LocalDate.of(2024, 9, 1));
        when(invoiceRepository.findAll()).thenReturn(List.of(inv));

        List<InvoiceResponse> responses = invoiceService.getAllInvoices();

        assertThat(responses).hasSize(1);
        assertThat(responses.get(0).getId()).isEqualTo(5L);
        assertThat(responses.get(0).getInvoiceNumber()).isEqualTo("INV-005");
        assertThat(responses.get(0).getDownloadUrl()).isEqualTo("/api/invoices/5/pdf");
    }

    @Test
    @DisplayName("getAllInvoices should return empty list when no invoices exist")
    void getAllInvoices_shouldReturnEmptyListWhenNoneExist() {
        when(invoiceRepository.findAll()).thenReturn(List.of());

        List<InvoiceResponse> responses = invoiceService.getAllInvoices();

        assertThat(responses).isEmpty();
    }

    // ── getInvoice tests ──────────────────────────────────────────────────────

    @Test
    @DisplayName("getInvoice should return the correct InvoiceResponse")
    void getInvoice_shouldReturnCorrectDto() {
        Invoice inv = buildPersistedInvoice(3L, "INV-003", "Delta Corp", "d@d.com",
                new BigDecimal("1000.00"), "USD", LocalDate.of(2024, 10, 5));
        when(invoiceRepository.findById(3L)).thenReturn(Optional.of(inv));

        InvoiceResponse response = invoiceService.getInvoice(3L);

        assertThat(response.getId()).isEqualTo(3L);
        assertThat(response.getInvoiceNumber()).isEqualTo("INV-003");
        assertThat(response.getTotalAmount()).isEqualByComparingTo(new BigDecimal("1000.00"));
    }

    @Test
    @DisplayName("getInvoice should throw NoSuchElementException for unknown id")
    void getInvoice_shouldThrowForUnknownId() {
        when(invoiceRepository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> invoiceService.getInvoice(42L))
                .isInstanceOf(NoSuchElementException.class)
                .hasMessageContaining("42");
    }

    // ── Private helpers ───────────────────────────────────────────────────────

    /**
     * Creates a realistic {@link Invoice} instance with the given id (simulating
     * what {@code invoiceRepository.save()} would return after persisting).
     *
     * <p>We use reflection to set the id because the JPA {@code @Id} field has
     * no public setter – the database normally assigns it.
     */
    private Invoice buildPersistedInvoice(Long id, String invoiceNumber, String customerName,
                                          String customerEmail, BigDecimal total,
                                          String currency, LocalDate issueDate) {
        Invoice invoice = new Invoice(invoiceNumber, customerName, customerEmail,
                total, currency, issueDate, null);
        // Inject the id via reflection (mimicking what JPA would do after persist)
        try {
            java.lang.reflect.Field idField = Invoice.class.getDeclaredField("id");
            idField.setAccessible(true);
            idField.set(invoice, id);
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException("Could not set invoice id for test", e);
        }
        return invoice;
    }
}
