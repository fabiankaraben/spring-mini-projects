package com.example.barcodegenerator.domain;

/**
 * Supported barcode/QR-code formats exposed by this API.
 *
 * <p>Each constant maps to the corresponding ZXing
 * {@link com.google.zxing.BarcodeFormat} and carries a human-readable
 * description for documentation purposes.
 *
 * <p>The enum name is what clients pass as the {@code format} query
 * parameter (case-insensitive conversion is handled in the controller).
 */
public enum BarcodeFormat {

    /**
     * QR Code – 2-D matrix barcode that can encode URLs, text, and binary data.
     * Maximum capacity ~4 KB of data.
     */
    QR_CODE,

    /**
     * Code 128 – high-density 1-D barcode that encodes all 128 ASCII characters.
     * Widely used in shipping, packaging and logistics.
     */
    CODE_128,

    /**
     * EAN-13 – 13-digit barcode used on retail consumer goods worldwide.
     * Encodes exactly 13 numeric digits (the last digit is a check digit).
     */
    EAN_13,

    /**
     * UPC-A – 12-digit barcode used primarily in North-American retail.
     * Encodes exactly 12 numeric digits.
     */
    UPC_A,

    /**
     * Code 39 – variable-length 1-D barcode supporting uppercase letters,
     * digits 0-9 and a small set of special characters.
     */
    CODE_39,

    /**
     * PDF 417 – 2-D stacked barcode commonly found on airline boarding passes,
     * identity documents and transport labels.
     */
    PDF_417
}
