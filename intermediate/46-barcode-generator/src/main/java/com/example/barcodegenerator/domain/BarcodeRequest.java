package com.example.barcodegenerator.domain;

/**
 * Immutable value object that holds all parameters needed to generate a
 * single barcode or QR code image.
 *
 * <p>Instances are created by the controller after validating the incoming
 * HTTP query parameters and are then passed to {@link com.example.barcodegenerator.service.BarcodeService}.
 *
 * @param content the data to encode inside the barcode (e.g. a URL or product code)
 * @param format  the barcode format to use (QR_CODE, CODE_128, EAN_13, …)
 * @param width   the desired image width in pixels
 * @param height  the desired image height in pixels
 */
public record BarcodeRequest(
        String content,
        BarcodeFormat format,
        int width,
        int height
) {
}
