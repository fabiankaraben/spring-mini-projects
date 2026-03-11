package com.example.barcodegenerator.exception;

/**
 * Thrown by {@link com.example.barcodegenerator.service.BarcodeService} when
 * ZXing cannot encode the requested content in the chosen barcode format.
 *
 * <p>Common causes:
 * <ul>
 *   <li>EAN-13 or UPC-A content that contains non-numeric characters.</li>
 *   <li>EAN-13 content that is not exactly 12 digits (the 13th check digit is
 *       computed automatically, so clients must supply 12 digits).</li>
 *   <li>Content that is too long for the requested image dimensions.</li>
 * </ul>
 *
 * <p>This is an unchecked exception; it is caught by
 * {@link com.example.barcodegenerator.controller.GlobalExceptionHandler} and
 * translated to an HTTP 400 Bad Request response.
 */
public class BarcodeGenerationException extends RuntimeException {

    /**
     * Creates a new exception with a descriptive message and the ZXing cause.
     *
     * @param message human-readable explanation of what went wrong
     * @param cause   the original ZXing or I/O exception
     */
    public BarcodeGenerationException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Creates a new exception with only a descriptive message (no cause).
     *
     * @param message human-readable explanation of what went wrong
     */
    public BarcodeGenerationException(String message) {
        super(message);
    }
}
