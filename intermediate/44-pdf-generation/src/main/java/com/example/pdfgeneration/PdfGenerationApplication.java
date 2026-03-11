package com.example.pdfgeneration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the PDF Generation Spring Boot application.
 *
 * <p>This mini-project demonstrates how to generate downloadable PDF documents
 * from a Spring Boot REST API using the OpenPDF library (a free, LGPL/MPL-licensed
 * fork of iText 2.x).
 *
 * <p>Key capabilities:
 * <ul>
 *   <li>Generate an invoice PDF from a JSON request body and return it as a
 *       downloadable byte stream.</li>
 *   <li>Generate a simple report PDF listing all stored invoices.</li>
 *   <li>Persist invoice metadata (number, customer, amount, date) in PostgreSQL.</li>
 * </ul>
 *
 * <p>{@code @SpringBootApplication} is a convenience annotation that combines:
 * <ul>
 *   <li>{@code @Configuration} – marks the class as a source of bean definitions.</li>
 *   <li>{@code @EnableAutoConfiguration} – activates Spring Boot's auto-configuration.</li>
 *   <li>{@code @ComponentScan} – scans this package and sub-packages for components.</li>
 * </ul>
 */
@SpringBootApplication
public class PdfGenerationApplication {

    public static void main(String[] args) {
        SpringApplication.run(PdfGenerationApplication.class, args);
    }
}
