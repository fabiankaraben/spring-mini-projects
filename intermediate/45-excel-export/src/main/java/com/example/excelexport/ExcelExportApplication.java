package com.example.excelexport;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Excel Export mini-project.
 *
 * <p>This application demonstrates how to generate and stream Excel (.xlsx) files
 * from a Spring Boot REST API using Apache POI's XSSF API.
 *
 * <p>Key concepts covered:
 * <ul>
 *   <li>Apache POI XSSFWorkbook – creating workbooks, sheets, rows, and cells</li>
 *   <li>Cell styles – fonts, colours, borders, number formats</li>
 *   <li>Streaming the binary response directly to the HTTP client</li>
 *   <li>Content-Disposition header – triggering a browser download</li>
 * </ul>
 */
@SpringBootApplication
public class ExcelExportApplication {

    public static void main(String[] args) {
        SpringApplication.run(ExcelExportApplication.class, args);
    }
}
