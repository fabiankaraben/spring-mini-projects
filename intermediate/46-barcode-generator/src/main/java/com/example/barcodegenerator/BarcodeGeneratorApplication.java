package com.example.barcodegenerator;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Barcode Generator Spring Boot application.
 *
 * <p>This application exposes REST endpoints that generate QR codes and
 * standard 1-D barcodes (Code 128, EAN-13, UPC-A, etc.) and return them
 * as PNG image streams using the ZXing (Zebra Crossing) library.
 *
 * <p>No database is required – barcodes are generated on-the-fly and
 * streamed directly back to the HTTP client.
 */
@SpringBootApplication
public class BarcodeGeneratorApplication {

    public static void main(String[] args) {
        SpringApplication.run(BarcodeGeneratorApplication.class, args);
    }
}
