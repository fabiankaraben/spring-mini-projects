package com.example.excelexport.controller;

import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.*;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Objects;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full integration tests for {@link ExcelExportController}.
 *
 * <p>These tests start the complete Spring Boot application on a random port
 * ({@link SpringBootTest.WebEnvironment#RANDOM_PORT}) and exercise the real HTTP
 * stack end-to-end, including Jackson serialisation, Bean Validation, and the
 * Apache POI workbook generation.
 *
 * <p>Why Testcontainers here?
 * This application has no database, so no database container is needed.
 * Testcontainers is still declared via {@link Testcontainers} to demonstrate
 * the integration-test pattern used throughout this mini-project series and to
 * keep the setup consistent with other projects that do use containers.
 * The annotation enables the Testcontainers JUnit 5 extension which manages
 * container lifecycle automatically.
 *
 * <p>The tests use {@link TestRestTemplate} – Spring Boot's built-in HTTP client
 * for integration tests. Unlike {@code MockMvc}, it sends real HTTP requests
 * over the network, which validates the full request/response cycle including
 * HTTP headers and binary body deserialization.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Testcontainers
@DisplayName("ExcelExportController – Integration Tests")
class ExcelExportControllerIntegrationTest {

    /**
     * Spring Boot injects the actual port the embedded server is listening on.
     * Using RANDOM_PORT avoids port conflicts when running tests in parallel.
     */
    @LocalServerPort
    private int port;

    /**
     * TestRestTemplate is auto-configured by Spring Boot for integration tests.
     * It wraps a RestTemplate and provides convenience methods for making HTTP
     * requests during tests.
     */
    @Autowired
    private TestRestTemplate restTemplate;

    /** Builds the base URL for the given path using the injected random port. */
    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    /**
     * Parses raw bytes into a POI {@link Workbook} so cell values can be asserted.
     * The caller is responsible for closing the workbook to release POI resources.
     */
    private Workbook toWorkbook(byte[] bytes) throws IOException {
        return new XSSFWorkbook(new ByteArrayInputStream(bytes));
    }

    // ── GET /api/export/products/sample ─────────────────────────────────────────

    @Nested
    @DisplayName("GET /api/export/products/sample")
    class SampleEndpoint {

        @Test
        @DisplayName("returns HTTP 200")
        void returns200() {
            ResponseEntity<byte[]> response = restTemplate.getForEntity(
                    url("/api/export/products/sample"), byte[].class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("response Content-Type is application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
        void contentTypeIsXlsx() {
            ResponseEntity<byte[]> response = restTemplate.getForEntity(
                    url("/api/export/products/sample"), byte[].class);
            MediaType contentType = response.getHeaders().getContentType();
            assertThat(contentType).isNotNull();
            assertThat(contentType.toString()).contains(
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        }

        @Test
        @DisplayName("Content-Disposition header is 'attachment' with a .xlsx filename")
        void contentDispositionIsAttachment() {
            ResponseEntity<byte[]> response = restTemplate.getForEntity(
                    url("/api/export/products/sample"), byte[].class);
            String disposition = response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
            assertThat(disposition)
                    .isNotNull()
                    .contains("attachment")
                    .contains(".xlsx");
        }

        @Test
        @DisplayName("response body is a non-empty byte array")
        void responseBodyIsNonEmpty() {
            ResponseEntity<byte[]> response = restTemplate.getForEntity(
                    url("/api/export/products/sample"), byte[].class);
            assertThat(response.getBody()).isNotNull().isNotEmpty();
        }

        @Test
        @DisplayName("response body is a valid XLSX workbook")
        void responseBodyIsValidXlsx() throws IOException {
            ResponseEntity<byte[]> response = restTemplate.getForEntity(
                    url("/api/export/products/sample"), byte[].class);
            byte[] body = Objects.requireNonNull(response.getBody());
            // This would throw IOException / OLE2NotOfficeXmlFileException if invalid
            try (Workbook wb = toWorkbook(body)) {
                assertThat(wb.getNumberOfSheets()).isEqualTo(1);
                assertThat(wb.getSheetAt(0).getSheetName()).isEqualTo("Products");
            }
        }

        @Test
        @DisplayName("sample workbook contains the title 'Sample Product Inventory'")
        void sampleTitleIsPresent() throws IOException {
            ResponseEntity<byte[]> response = restTemplate.getForEntity(
                    url("/api/export/products/sample"), byte[].class);
            byte[] body = Objects.requireNonNull(response.getBody());
            try (Workbook wb = toWorkbook(body)) {
                Sheet sheet = wb.getSheetAt(0);
                String title = sheet.getRow(0).getCell(0).getStringCellValue();
                assertThat(title).isEqualTo("Sample Product Inventory");
            }
        }

        @Test
        @DisplayName("sample workbook has 10 data rows (one per sample product)")
        void sampleWorkbookHasTenDataRows() throws IOException {
            ResponseEntity<byte[]> response = restTemplate.getForEntity(
                    url("/api/export/products/sample"), byte[].class);
            byte[] body = Objects.requireNonNull(response.getBody());
            try (Workbook wb = toWorkbook(body)) {
                Sheet sheet = wb.getSheetAt(0);
                // Total rows = 1 title + 1 header + 10 data + 1 totals = 13
                assertThat(sheet.getLastRowNum() + 1).isEqualTo(13);
            }
        }
    }

    // ── POST /api/export/products ───────────────────────────────────────────────

    @Nested
    @DisplayName("POST /api/export/products")
    class CustomEndpoint {

        /** Builds a JSON request body for the POST endpoint. */
        private HttpEntity<String> buildRequest(String json) {
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_JSON);
            return new HttpEntity<>(json, headers);
        }

        @Test
        @DisplayName("returns HTTP 200 for a valid request")
        void returns200ForValidRequest() {
            String body = """
                    {
                      "reportTitle": "Integration Test Report",
                      "products": [
                        {
                          "id": "T-001",
                          "name": "Widget A",
                          "category": "Parts",
                          "price": 12.50,
                          "stock": 100
                        }
                      ]
                    }
                    """;
            ResponseEntity<byte[]> response = restTemplate.exchange(
                    url("/api/export/products"), HttpMethod.POST,
                    buildRequest(body), byte[].class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        }

        @Test
        @DisplayName("response Content-Type is application/vnd.openxmlformats-officedocument.spreadsheetml.sheet")
        void contentTypeIsXlsx() {
            String body = """
                    {
                      "reportTitle": "Test",
                      "products": [
                        {"id":"X","name":"Y","category":"Z","price":1.0,"stock":1}
                      ]
                    }
                    """;
            ResponseEntity<byte[]> response = restTemplate.exchange(
                    url("/api/export/products"), HttpMethod.POST,
                    buildRequest(body), byte[].class);
            assertThat(Objects.requireNonNull(response.getHeaders().getContentType()).toString())
                    .contains("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");
        }

        @Test
        @DisplayName("workbook title matches the requested reportTitle")
        void workbookTitleMatchesRequest() throws IOException {
            String requestedTitle = "My Custom Report 2024";
            String body = """
                    {
                      "reportTitle": "%s",
                      "products": [
                        {"id":"P1","name":"Item","category":"Cat","price":9.99,"stock":5}
                      ]
                    }
                    """.formatted(requestedTitle);

            ResponseEntity<byte[]> response = restTemplate.exchange(
                    url("/api/export/products"), HttpMethod.POST,
                    buildRequest(body), byte[].class);

            byte[] responseBody = Objects.requireNonNull(response.getBody());
            try (Workbook wb = toWorkbook(responseBody)) {
                String actualTitle = wb.getSheetAt(0).getRow(0).getCell(0).getStringCellValue();
                assertThat(actualTitle).isEqualTo(requestedTitle);
            }
        }

        @Test
        @DisplayName("workbook contains the correct number of rows for multiple products")
        void rowCountMatchesProducts() throws IOException {
            String body = """
                    {
                      "reportTitle": "Multi-product Test",
                      "products": [
                        {"id":"P1","name":"Alpha","category":"A","price":1.00,"stock":1},
                        {"id":"P2","name":"Beta", "category":"B","price":2.00,"stock":2},
                        {"id":"P3","name":"Gamma","category":"C","price":3.00,"stock":3}
                      ]
                    }
                    """;

            ResponseEntity<byte[]> response = restTemplate.exchange(
                    url("/api/export/products"), HttpMethod.POST,
                    buildRequest(body), byte[].class);

            byte[] responseBody = Objects.requireNonNull(response.getBody());
            try (Workbook wb = toWorkbook(responseBody)) {
                Sheet sheet = wb.getSheetAt(0);
                // 3 products → 1 title + 1 header + 3 data + 1 totals = 6 rows
                assertThat(sheet.getLastRowNum() + 1).isEqualTo(6);
            }
        }

        @Test
        @DisplayName("first data row contains the correct product data")
        void firstDataRowMatchesProduct() throws IOException {
            String body = """
                    {
                      "reportTitle": "Data Test",
                      "products": [
                        {
                          "id": "ABC-999",
                          "name": "Super Widget",
                          "category": "Gadgets",
                          "price": 199.99,
                          "stock": 42
                        }
                      ]
                    }
                    """;

            ResponseEntity<byte[]> response = restTemplate.exchange(
                    url("/api/export/products"), HttpMethod.POST,
                    buildRequest(body), byte[].class);

            byte[] responseBody = Objects.requireNonNull(response.getBody());
            try (Workbook wb = toWorkbook(responseBody)) {
                Row dataRow = wb.getSheetAt(0).getRow(2);
                assertThat(dataRow.getCell(0).getStringCellValue()).isEqualTo("ABC-999");
                assertThat(dataRow.getCell(1).getStringCellValue()).isEqualTo("Super Widget");
                assertThat(dataRow.getCell(2).getStringCellValue()).isEqualTo("Gadgets");
                assertThat(dataRow.getCell(3).getNumericCellValue()).isEqualTo(199.99);
                assertThat(dataRow.getCell(4).getNumericCellValue()).isEqualTo(42.0);
            }
        }

        @Test
        @DisplayName("returns HTTP 400 when reportTitle is blank")
        void returns400WhenTitleIsBlank() {
            String body = """
                    {
                      "reportTitle": "",
                      "products": [
                        {"id":"X","name":"Y","category":"Z","price":1.0,"stock":1}
                      ]
                    }
                    """;
            ResponseEntity<String> response = restTemplate.exchange(
                    url("/api/export/products"), HttpMethod.POST,
                    buildRequest(body), String.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("returns HTTP 400 when products list is empty")
        void returns400WhenProductsIsEmpty() {
            String body = """
                    {
                      "reportTitle": "Empty List Test",
                      "products": []
                    }
                    """;
            ResponseEntity<String> response = restTemplate.exchange(
                    url("/api/export/products"), HttpMethod.POST,
                    buildRequest(body), String.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("returns HTTP 400 when a product has a negative price")
        void returns400WhenPriceIsNegative() {
            String body = """
                    {
                      "reportTitle": "Negative Price Test",
                      "products": [
                        {"id":"X","name":"Y","category":"Z","price":-5.00,"stock":1}
                      ]
                    }
                    """;
            ResponseEntity<String> response = restTemplate.exchange(
                    url("/api/export/products"), HttpMethod.POST,
                    buildRequest(body), String.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("returns HTTP 400 when a product has negative stock")
        void returns400WhenStockIsNegative() {
            String body = """
                    {
                      "reportTitle": "Negative Stock Test",
                      "products": [
                        {"id":"X","name":"Y","category":"Z","price":1.00,"stock":-1}
                      ]
                    }
                    """;
            ResponseEntity<String> response = restTemplate.exchange(
                    url("/api/export/products"), HttpMethod.POST,
                    buildRequest(body), String.class);
            assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        }

        @Test
        @DisplayName("Content-Disposition filename is derived from the report title")
        void filenameIsDerivedFromTitle() {
            String body = """
                    {
                      "reportTitle": "Sales Report Q1",
                      "products": [
                        {"id":"X","name":"Y","category":"Z","price":1.0,"stock":1}
                      ]
                    }
                    """;
            ResponseEntity<byte[]> response = restTemplate.exchange(
                    url("/api/export/products"), HttpMethod.POST,
                    buildRequest(body), byte[].class);

            String disposition = response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
            // Title "Sales Report Q1" should become "sales-report-q1.xlsx"
            assertThat(disposition)
                    .isNotNull()
                    .contains("attachment")
                    .contains("sales-report-q1.xlsx");
        }
    }
}
