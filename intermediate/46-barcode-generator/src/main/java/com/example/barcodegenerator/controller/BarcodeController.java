package com.example.barcodegenerator.controller;

import com.example.barcodegenerator.domain.BarcodeFormat;
import com.example.barcodegenerator.domain.BarcodeRequest;
import com.example.barcodegenerator.service.BarcodeService;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller that exposes endpoints for generating barcode and QR code
 * images and returning them as PNG byte streams.
 *
 * <h2>Endpoints</h2>
 * <ul>
 *   <li>{@code GET /api/barcode} – generate any supported format (QR, Code 128,
 *       EAN-13, UPC-A, Code 39, PDF 417) by supplying the {@code format}
 *       query parameter.</li>
 *   <li>{@code GET /api/barcode/qr} – convenience shortcut that always produces
 *       a QR code without requiring the {@code format} parameter.</li>
 * </ul>
 *
 * <h2>Response</h2>
 * Both endpoints respond with {@code Content-Type: image/png} and a raw PNG
 * image body. HTTP clients (browsers, curl, etc.) can display or save the
 * image directly.
 *
 * <h2>Validation</h2>
 * {@link Validated} activates Bean Validation on method parameters so that
 * Spring rejects invalid requests with a 400 before they reach the service.
 */
@RestController
@RequestMapping("/api/barcode")
@Validated
public class BarcodeController {

    /**
     * Service that delegates to ZXing for the actual image generation.
     * Injected by Spring through constructor injection (preferred over field injection).
     */
    private final BarcodeService barcodeService;

    /**
     * Default image width in pixels used when the {@code width} parameter is omitted.
     */
    private static final int DEFAULT_WIDTH = 300;

    /**
     * Default image height in pixels used when the {@code height} parameter is omitted.
     */
    private static final int DEFAULT_HEIGHT = 300;

    /**
     * Constructs the controller with the given {@link BarcodeService}.
     *
     * @param barcodeService the service responsible for generating barcode images
     */
    public BarcodeController(BarcodeService barcodeService) {
        this.barcodeService = barcodeService;
    }

    /**
     * Generates a barcode or QR code in the specified format and returns it as a
     * PNG image stream.
     *
     * <p>Example usage:
     * <pre>
     * GET /api/barcode?content=Hello+World&amp;format=QR_CODE
     * GET /api/barcode?content=012345678901&amp;format=EAN_13&amp;width=400&amp;height=150
     * </pre>
     *
     * @param content the data to encode; must not be blank
     * @param format  the barcode format (QR_CODE, CODE_128, EAN_13, UPC_A, CODE_39, PDF_417)
     * @param width   image width in pixels (10–2000, default 300)
     * @param height  image height in pixels (10–2000, default 300)
     * @return a {@link ResponseEntity} containing the PNG image bytes
     */
    @GetMapping(produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> generateBarcode(
            @RequestParam @NotBlank(message = "content must not be blank") String content,
            @RequestParam(defaultValue = "QR_CODE") BarcodeFormat format,
            @RequestParam(defaultValue = "" + DEFAULT_WIDTH)
                @Min(value = 10, message = "width must be at least 10 pixels")
                @Max(value = 2000, message = "width must not exceed 2000 pixels") int width,
            @RequestParam(defaultValue = "" + DEFAULT_HEIGHT)
                @Min(value = 10, message = "height must be at least 10 pixels")
                @Max(value = 2000, message = "height must not exceed 2000 pixels") int height
    ) {
        // Assemble the domain object and delegate to the service.
        BarcodeRequest request = new BarcodeRequest(content, format, width, height);
        byte[] imageBytes = barcodeService.generate(request);

        // Return the raw PNG bytes with the correct Content-Type header.
        // Spring automatically sets Content-Length from the byte array length.
        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(imageBytes);
    }

    /**
     * Convenience endpoint that always generates a QR code.
     *
     * <p>Clients that only need QR codes can use this endpoint without specifying
     * the {@code format} parameter.
     *
     * <p>Example usage:
     * <pre>
     * GET /api/barcode/qr?content=https://example.com
     * GET /api/barcode/qr?content=Hello+World&amp;width=500&amp;height=500
     * </pre>
     *
     * @param content the data to encode in the QR code; must not be blank
     * @param width   image width in pixels (10–2000, default 300)
     * @param height  image height in pixels (10–2000, default 300)
     * @return a {@link ResponseEntity} containing the PNG image bytes
     */
    @GetMapping(value = "/qr", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<byte[]> generateQrCode(
            @RequestParam @NotBlank(message = "content must not be blank") String content,
            @RequestParam(defaultValue = "" + DEFAULT_WIDTH)
                @Min(value = 10, message = "width must be at least 10 pixels")
                @Max(value = 2000, message = "width must not exceed 2000 pixels") int width,
            @RequestParam(defaultValue = "" + DEFAULT_HEIGHT)
                @Min(value = 10, message = "height must be at least 10 pixels")
                @Max(value = 2000, message = "height must not exceed 2000 pixels") int height
    ) {
        // Hard-wire the format to QR_CODE for this convenience endpoint.
        BarcodeRequest request = new BarcodeRequest(content, BarcodeFormat.QR_CODE, width, height);
        byte[] imageBytes = barcodeService.generate(request);

        return ResponseEntity.ok()
                .contentType(MediaType.IMAGE_PNG)
                .body(imageBytes);
    }
}
