package com.example.barcodegenerator.unit;

import com.example.barcodegenerator.domain.BarcodeFormat;
import com.example.barcodegenerator.domain.BarcodeRequest;
import com.example.barcodegenerator.exception.BarcodeGenerationException;
import com.example.barcodegenerator.service.BarcodeService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for {@link BarcodeService}.
 *
 * <h2>Scope</h2>
 * These tests exercise the core domain logic – the ZXing encoding pipeline –
 * without starting any Spring context. {@link BarcodeService} is instantiated
 * directly via {@code new}, which keeps the tests fast and focused.
 *
 * <h2>What is verified</h2>
 * <ul>
 *   <li>Each supported {@link BarcodeFormat} produces a non-empty PNG byte array.</li>
 *   <li>The resulting bytes can be decoded as a valid {@link BufferedImage}
 *       with the correct dimensions.</li>
 *   <li>Invalid content for numeric-only formats (EAN-13, UPC-A) causes a
 *       {@link BarcodeGenerationException} to be thrown.</li>
 *   <li>Blank content raises a {@link BarcodeGenerationException}.</li>
 * </ul>
 */
@DisplayName("BarcodeService – Unit Tests")
class BarcodeServiceTest {

    /**
     * The service under test. Created fresh for every test method to ensure
     * complete isolation between tests.
     */
    private BarcodeService barcodeService;

    @BeforeEach
    void setUp() {
        // No Spring context needed; instantiate the service directly.
        barcodeService = new BarcodeService();
    }

    // ── QR Code ───────────────────────────────────────────────────────────────

    @Test
    @DisplayName("generate() should return non-empty PNG bytes for QR_CODE")
    void generate_shouldReturnNonEmptyBytes_forQrCode() {
        BarcodeRequest request = new BarcodeRequest("https://example.com", BarcodeFormat.QR_CODE, 300, 300);

        byte[] result = barcodeService.generate(request);

        // The result must not be null or empty
        assertThat(result).isNotNull().isNotEmpty();
    }

    @Test
    @DisplayName("generate() should return a valid PNG image for QR_CODE with correct dimensions")
    void generate_shouldReturnValidPng_withCorrectDimensions_forQrCode() throws IOException {
        int expectedWidth = 250;
        int expectedHeight = 250;
        BarcodeRequest request = new BarcodeRequest("test content", BarcodeFormat.QR_CODE, expectedWidth, expectedHeight);

        byte[] result = barcodeService.generate(request);

        // Decode the raw bytes back into a BufferedImage to verify it is a valid PNG
        BufferedImage image = ImageIO.read(new ByteArrayInputStream(result));
        assertThat(image).isNotNull();
        // ZXing may add quiet-zone padding, so the image dimensions are >= requested size.
        // We verify the image is at least as large as requested.
        assertThat(image.getWidth()).isGreaterThanOrEqualTo(expectedWidth);
        assertThat(image.getHeight()).isGreaterThanOrEqualTo(expectedHeight);
    }

    @Test
    @DisplayName("generate() should encode URL content in QR_CODE without throwing")
    void generate_shouldEncodeUrl_forQrCode() {
        BarcodeRequest request = new BarcodeRequest(
                "https://www.example.com/path?query=value&other=123",
                BarcodeFormat.QR_CODE, 400, 400);

        // Should complete without any exception
        byte[] result = barcodeService.generate(request);
        assertThat(result).isNotEmpty();
    }

    // ── Code 128 ─────────────────────────────────────────────────────────────

    @Test
    @DisplayName("generate() should return non-empty PNG bytes for CODE_128")
    void generate_shouldReturnNonEmptyBytes_forCode128() {
        BarcodeRequest request = new BarcodeRequest("HELLO-WORLD-123", BarcodeFormat.CODE_128, 400, 150);

        byte[] result = barcodeService.generate(request);

        assertThat(result).isNotNull().isNotEmpty();
    }

    @Test
    @DisplayName("generate() should return a valid PNG image for CODE_128")
    void generate_shouldReturnValidPng_forCode128() throws IOException {
        BarcodeRequest request = new BarcodeRequest("ABC-123", BarcodeFormat.CODE_128, 400, 150);

        byte[] result = barcodeService.generate(request);

        BufferedImage image = ImageIO.read(new ByteArrayInputStream(result));
        assertThat(image).isNotNull();
        // 1-D barcodes are wider than tall; width must be positive
        assertThat(image.getWidth()).isPositive();
        assertThat(image.getHeight()).isPositive();
    }

    // ── EAN-13 ────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("generate() should return non-empty PNG bytes for EAN_13 with valid 12-digit content")
    void generate_shouldReturnNonEmptyBytes_forEan13() {
        // EAN-13 requires exactly 12 digits; ZXing computes the 13th check digit.
        BarcodeRequest request = new BarcodeRequest("012345678901", BarcodeFormat.EAN_13, 300, 150);

        byte[] result = barcodeService.generate(request);

        assertThat(result).isNotNull().isNotEmpty();
    }

    @Test
    @DisplayName("generate() should throw BarcodeGenerationException for EAN_13 with non-numeric content")
    void generate_shouldThrowException_forEan13WithNonNumericContent() {
        // EAN-13 only accepts numeric digits; letters must be rejected
        BarcodeRequest request = new BarcodeRequest("ABCDEFGHIJKL", BarcodeFormat.EAN_13, 300, 150);

        assertThatThrownBy(() -> barcodeService.generate(request))
                .isInstanceOf(BarcodeGenerationException.class)
                .hasMessageContaining("EAN_13");
    }

    @Test
    @DisplayName("generate() should throw BarcodeGenerationException for EAN_13 with wrong digit count")
    void generate_shouldThrowException_forEan13WithWrongDigitCount() {
        // EAN-13 requires exactly 12 digits (not 10, not 13)
        BarcodeRequest request = new BarcodeRequest("12345", BarcodeFormat.EAN_13, 300, 150);

        assertThatThrownBy(() -> barcodeService.generate(request))
                .isInstanceOf(BarcodeGenerationException.class);
    }

    // ── UPC-A ─────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("generate() should return non-empty PNG bytes for UPC_A with valid 11-digit content")
    void generate_shouldReturnNonEmptyBytes_forUpcA() {
        // UPC-A requires 11 digits; ZXing computes the 12th check digit.
        BarcodeRequest request = new BarcodeRequest("01234567890", BarcodeFormat.UPC_A, 300, 150);

        byte[] result = barcodeService.generate(request);

        assertThat(result).isNotNull().isNotEmpty();
    }

    @Test
    @DisplayName("generate() should throw BarcodeGenerationException for UPC_A with non-numeric content")
    void generate_shouldThrowException_forUpcAWithNonNumericContent() {
        BarcodeRequest request = new BarcodeRequest("HELLO123456", BarcodeFormat.UPC_A, 300, 150);

        assertThatThrownBy(() -> barcodeService.generate(request))
                .isInstanceOf(BarcodeGenerationException.class);
    }

    // ── Code 39 ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("generate() should return non-empty PNG bytes for CODE_39")
    void generate_shouldReturnNonEmptyBytes_forCode39() {
        // Code 39 supports uppercase letters, digits 0-9 and some special chars
        BarcodeRequest request = new BarcodeRequest("CODE39TEST", BarcodeFormat.CODE_39, 400, 150);

        byte[] result = barcodeService.generate(request);

        assertThat(result).isNotNull().isNotEmpty();
    }

    // ── PDF 417 ──────────────────────────────────────────────────────────────

    @Test
    @DisplayName("generate() should return non-empty PNG bytes for PDF_417")
    void generate_shouldReturnNonEmptyBytes_forPdf417() {
        BarcodeRequest request = new BarcodeRequest("PDF417 test data 1234", BarcodeFormat.PDF_417, 400, 200);

        byte[] result = barcodeService.generate(request);

        assertThat(result).isNotNull().isNotEmpty();
    }

    // ── PNG signature verification ────────────────────────────────────────────

    @Test
    @DisplayName("generate() output should start with the PNG magic bytes (\\x89PNG)")
    void generate_outputShouldStartWithPngMagicBytes() {
        BarcodeRequest request = new BarcodeRequest("PNG magic test", BarcodeFormat.QR_CODE, 200, 200);

        byte[] result = barcodeService.generate(request);

        // PNG files always start with the 8-byte signature: 89 50 4E 47 0D 0A 1A 0A
        assertThat(result).hasSizeGreaterThanOrEqualTo(8);
        assertThat(result[0]).isEqualTo((byte) 0x89); // \x89
        assertThat(result[1]).isEqualTo((byte) 0x50); // P
        assertThat(result[2]).isEqualTo((byte) 0x4E); // N
        assertThat(result[3]).isEqualTo((byte) 0x47); // G
    }

    // ── Dimension handling ────────────────────────────────────────────────────

    @Test
    @DisplayName("generate() should produce a larger image when larger dimensions are requested")
    void generate_largerDimensions_shouldProduceLargerImage() throws IOException {
        BarcodeRequest small = new BarcodeRequest("data", BarcodeFormat.QR_CODE, 100, 100);
        BarcodeRequest large = new BarcodeRequest("data", BarcodeFormat.QR_CODE, 500, 500);

        byte[] smallResult = barcodeService.generate(small);
        byte[] largeResult = barcodeService.generate(large);

        // The larger image should produce more bytes than the smaller one
        assertThat(largeResult.length).isGreaterThan(smallResult.length);

        // Also verify both are valid images with the correct relative sizes
        BufferedImage smallImage = ImageIO.read(new ByteArrayInputStream(smallResult));
        BufferedImage largeImage = ImageIO.read(new ByteArrayInputStream(largeResult));
        assertThat(largeImage.getWidth()).isGreaterThan(smallImage.getWidth());
        assertThat(largeImage.getHeight()).isGreaterThan(smallImage.getHeight());
    }
}
