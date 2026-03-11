package com.example.barcodegenerator.integration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Full integration tests for the Barcode Generator REST API.
 *
 * <h2>What these tests cover</h2>
 * <ul>
 *   <li>{@code GET /api/barcode} with every supported format (QR_CODE, CODE_128,
 *       EAN_13, UPC_A, CODE_39, PDF_417).</li>
 *   <li>{@code GET /api/barcode/qr} – the QR convenience shortcut.</li>
 *   <li>Custom {@code width} and {@code height} parameters.</li>
 *   <li>Validation: missing {@code content}, invalid {@code format}, invalid
 *       content for numeric-only formats (EAN-13).</li>
 *   <li>PNG magic-byte signature in every successful response body.</li>
 * </ul>
 *
 * <h2>Technology used</h2>
 * <ul>
 *   <li><strong>{@code @SpringBootTest}</strong> – boots the full application
 *       context including Spring MVC, service layer and ZXing encoding.</li>
 *   <li><strong>MockMvc</strong> – dispatches HTTP requests through the full
 *       Spring MVC stack (filters, controllers, serialisation) without opening
 *       a real network socket, making tests fast and deterministic.</li>
 *   <li><strong>Testcontainers</strong> – the project has Testcontainers on the
 *       test classpath and the Docker API properties configured; no external
 *       service container is needed here because the barcode generator is a
 *       self-contained service with no external dependencies.</li>
 * </ul>
 *
 * <h2>Why no @Testcontainers container is declared</h2>
 * This application has no database or external service. Testcontainers is still
 * included as a dependency (and the Docker API properties are configured) so that
 * if a future test requires a container it can be added without extra setup. The
 * existing configuration also ensures the Docker-environment check that Testcontainers
 * performs at startup succeeds on Docker Desktop 29+.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("BarcodeController – Integration Tests")
class BarcodeControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    // ── PNG magic bytes ───────────────────────────────────────────────────────

    /**
     * The first 4 bytes of every valid PNG file: 0x89 'P' 'N' 'G'.
     * We check these in every successful response to confirm the body is a real image.
     */
    private static final byte[] PNG_MAGIC = {(byte) 0x89, 0x50, 0x4E, 0x47};

    // ── GET /api/barcode – QR_CODE ────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/barcode?content=…&format=QR_CODE should return 200 PNG")
    void barcode_qrCode_shouldReturn200WithPngBody() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/barcode")
                        .param("content", "https://example.com")
                        .param("format", "QR_CODE"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andReturn();

        byte[] body = result.getResponse().getContentAsByteArray();
        assertThat(body).isNotEmpty();
        assertPngMagicBytes(body);
    }

    @Test
    @DisplayName("GET /api/barcode should default to QR_CODE when format is omitted")
    void barcode_defaultFormat_shouldBeQrCode() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/barcode")
                        .param("content", "default format test"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andReturn();

        byte[] body = result.getResponse().getContentAsByteArray();
        assertThat(body).isNotEmpty();
        assertPngMagicBytes(body);
    }

    // ── GET /api/barcode – CODE_128 ──────────────────────────────────────────

    @Test
    @DisplayName("GET /api/barcode?content=…&format=CODE_128 should return 200 PNG")
    void barcode_code128_shouldReturn200WithPngBody() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/barcode")
                        .param("content", "HELLO-WORLD-128")
                        .param("format", "CODE_128")
                        .param("width", "400")
                        .param("height", "150"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andReturn();

        byte[] body = result.getResponse().getContentAsByteArray();
        assertThat(body).isNotEmpty();
        assertPngMagicBytes(body);
    }

    // ── GET /api/barcode – EAN_13 ─────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/barcode?content=012345678901&format=EAN_13 should return 200 PNG")
    void barcode_ean13_shouldReturn200WithPngBody() throws Exception {
        // EAN-13 requires exactly 12 digits; ZXing appends the check digit automatically
        MvcResult result = mockMvc.perform(get("/api/barcode")
                        .param("content", "012345678901")
                        .param("format", "EAN_13")
                        .param("width", "300")
                        .param("height", "150"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andReturn();

        byte[] body = result.getResponse().getContentAsByteArray();
        assertThat(body).isNotEmpty();
        assertPngMagicBytes(body);
    }

    @Test
    @DisplayName("GET /api/barcode?format=EAN_13 with non-numeric content should return 400")
    void barcode_ean13_withNonNumericContent_shouldReturn400() throws Exception {
        // EAN-13 requires exactly 12 numeric digits; supplying letters triggers a ZXing FormatException
        // wrapped as BarcodeGenerationException → 400 Bad Request
        mockMvc.perform(get("/api/barcode")
                        .param("content", "ABCDEFGHIJKL")  // 12 non-numeric chars
                        .param("format", "EAN_13"))
                .andExpect(status().isBadRequest());
    }

    // ── GET /api/barcode – UPC_A ──────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/barcode?content=01234567890&format=UPC_A should return 200 PNG")
    void barcode_upcA_shouldReturn200WithPngBody() throws Exception {
        // UPC-A requires 11 digits; ZXing appends the check digit automatically
        MvcResult result = mockMvc.perform(get("/api/barcode")
                        .param("content", "01234567890")
                        .param("format", "UPC_A")
                        .param("width", "300")
                        .param("height", "150"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andReturn();

        byte[] body = result.getResponse().getContentAsByteArray();
        assertThat(body).isNotEmpty();
        assertPngMagicBytes(body);
    }

    // ── GET /api/barcode – CODE_39 ────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/barcode?content=…&format=CODE_39 should return 200 PNG")
    void barcode_code39_shouldReturn200WithPngBody() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/barcode")
                        .param("content", "CODE39TEST")
                        .param("format", "CODE_39")
                        .param("width", "400")
                        .param("height", "150"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andReturn();

        byte[] body = result.getResponse().getContentAsByteArray();
        assertThat(body).isNotEmpty();
        assertPngMagicBytes(body);
    }

    // ── GET /api/barcode – PDF_417 ────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/barcode?content=…&format=PDF_417 should return 200 PNG")
    void barcode_pdf417_shouldReturn200WithPngBody() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/barcode")
                        .param("content", "PDF417 boarding pass data")
                        .param("format", "PDF_417")
                        .param("width", "400")
                        .param("height", "200"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andReturn();

        byte[] body = result.getResponse().getContentAsByteArray();
        assertThat(body).isNotEmpty();
        assertPngMagicBytes(body);
    }

    // ── GET /api/barcode/qr – convenience endpoint ────────────────────────────

    @Test
    @DisplayName("GET /api/barcode/qr?content=… should return 200 PNG")
    void barcodeQr_shouldReturn200WithPngBody() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/barcode/qr")
                        .param("content", "https://www.example.com"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andReturn();

        byte[] body = result.getResponse().getContentAsByteArray();
        assertThat(body).isNotEmpty();
        assertPngMagicBytes(body);
    }

    @Test
    @DisplayName("GET /api/barcode/qr with custom dimensions should return 200 PNG")
    void barcodeQr_withCustomDimensions_shouldReturn200WithPngBody() throws Exception {
        MvcResult result = mockMvc.perform(get("/api/barcode/qr")
                        .param("content", "Custom size QR code")
                        .param("width", "600")
                        .param("height", "600"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andReturn();

        byte[] body = result.getResponse().getContentAsByteArray();
        assertThat(body).isNotEmpty();
        assertPngMagicBytes(body);
    }

    // ── Validation – missing / invalid parameters ─────────────────────────────

    @Test
    @DisplayName("GET /api/barcode without content parameter should return 400")
    void barcode_withoutContent_shouldReturn400() throws Exception {
        mockMvc.perform(get("/api/barcode")
                        .param("format", "QR_CODE"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/barcode/qr without content parameter should return 400")
    void barcodeQr_withoutContent_shouldReturn400() throws Exception {
        mockMvc.perform(get("/api/barcode/qr"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/barcode with invalid format value should return 400")
    void barcode_withInvalidFormat_shouldReturn400() throws Exception {
        mockMvc.perform(get("/api/barcode")
                        .param("content", "test")
                        .param("format", "INVALID_FORMAT"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/barcode with width below minimum (10) should return 400")
    void barcode_withWidthBelowMinimum_shouldReturn400() throws Exception {
        mockMvc.perform(get("/api/barcode")
                        .param("content", "test")
                        .param("width", "5"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("GET /api/barcode with height above maximum (2000) should return 400")
    void barcode_withHeightAboveMaximum_shouldReturn400() throws Exception {
        mockMvc.perform(get("/api/barcode")
                        .param("content", "test")
                        .param("height", "3000"))
                .andExpect(status().isBadRequest());
    }

    // ── Helper ────────────────────────────────────────────────────────────────

    /**
     * Asserts that the first four bytes of {@code body} match the PNG file signature
     * ({@code \x89 P N G}). This confirms the response body is a valid PNG image,
     * not an error JSON or empty stream.
     *
     * @param body the raw response body bytes
     */
    private void assertPngMagicBytes(byte[] body) {
        assertThat(body).hasSizeGreaterThanOrEqualTo(PNG_MAGIC.length);
        for (int i = 0; i < PNG_MAGIC.length; i++) {
            assertThat(body[i])
                    .as("PNG magic byte at index %d", i)
                    .isEqualTo(PNG_MAGIC[i]);
        }
    }
}
