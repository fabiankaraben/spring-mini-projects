package com.example.serverless.integration;

import com.fasterxml.jackson.databind.JsonNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Full integration test for the Spring Cloud Function HTTP endpoints.
 *
 * <p>Uses {@link TestRestTemplate} with a real embedded Tomcat (RANDOM_PORT).
 * This avoids MockMvc async-dispatch complications with Spring Cloud Function's
 * {@code FunctionController}, which internally uses Reactor/Flux.
 *
 * <p>Spring Cloud Function auto-exposes each Function/Consumer bean at:
 * <pre>
 *   POST /{beanName}   — Content-Type: application/json
 * </pre>
 *
 * <p>No Docker containers are needed because all business logic runs in-process.
 *
 * <p>Test coverage:
 * <ol>
 *   <li>POST /calculateTax — California order (8.75% tax)</li>
 *   <li>POST /calculateTax — German order (19% VAT)</li>
 *   <li>POST /applyDiscount — SAVE10 code (10% off)</li>
 *   <li>POST /applyDiscount — unknown code (0% off)</li>
 *   <li>POST /generateInvoice — full invoice with SAVE20 discount</li>
 *   <li>POST /generateInvoice — invoice without discount</li>
 *   <li>POST /auditLogger — Consumer returns 200 OK</li>
 * </ol>
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@DisplayName("Function HTTP Integration Tests (full Spring context, no Docker)")
class FunctionHttpIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    // =========================================================================
    // Helper
    // =========================================================================

    /**
     * Builds a JSON request entity for the given body map.
     * Sets Content-Type and Accept to application/json.
     */
    private HttpEntity<Map<String, Object>> jsonEntity(Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.setAccept(java.util.List.of(MediaType.APPLICATION_JSON));
        return new HttpEntity<>(body, headers);
    }

    // =========================================================================
    // POST /calculateTax
    // =========================================================================

    @Test
    @DisplayName("POST /calculateTax — California order returns correct tax amounts")
    void calculateTaxForCaliforniaOrder() {
        // California: 8.75% tax on 200.00 = 17.50, total = 217.50
        Map<String, Object> body = Map.of(
                "orderId", "ORD-IT-001",
                "customerId", "CUST-IT-1",
                "subtotal", "200.00",
                "country", "US",
                "state", "CA"
        );

        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                "/calculateTax", jsonEntity(body), JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode json = response.getBody();
        assertThat(json).isNotNull();
        assertThat(json.get("orderId").asText()).isEqualTo("ORD-IT-001");
        assertThat(json.get("subtotal").asDouble()).isEqualTo(200.00);
        // taxRate = 0.0875
        assertThat(json.get("taxRate").asDouble()).isCloseTo(0.0875, org.assertj.core.data.Offset.offset(0.0001));
        // taxAmount = 200.00 x 0.0875 = 17.50
        assertThat(json.get("taxAmount").asDouble()).isCloseTo(17.50, org.assertj.core.data.Offset.offset(0.01));
        // total = 200.00 + 17.50 = 217.50
        assertThat(json.get("total").asDouble()).isCloseTo(217.50, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    @DisplayName("POST /calculateTax — German order returns 19% VAT")
    void calculateTaxForGermanOrder() {
        // Germany: 19% VAT on 100.00 = 19.00, total = 119.00
        Map<String, Object> body = Map.of(
                "orderId", "ORD-IT-002",
                "customerId", "CUST-IT-2",
                "subtotal", "100.00",
                "country", "DE"
        );

        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                "/calculateTax", jsonEntity(body), JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode json = response.getBody();
        assertThat(json).isNotNull();
        assertThat(json.get("taxRate").asDouble()).isCloseTo(0.19, org.assertj.core.data.Offset.offset(0.001));
        assertThat(json.get("taxAmount").asDouble()).isCloseTo(19.00, org.assertj.core.data.Offset.offset(0.01));
        assertThat(json.get("total").asDouble()).isCloseTo(119.00, org.assertj.core.data.Offset.offset(0.01));
    }

    // =========================================================================
    // POST /applyDiscount
    // =========================================================================

    @Test
    @DisplayName("POST /applyDiscount — SAVE10 produces 10% discount")
    void applyDiscountWithSave10Code() {
        // SAVE10 = 10% off 300.00 = 30.00 discount, finalTotal = 270.00
        Map<String, Object> body = Map.of(
                "orderId", "ORD-IT-003",
                "originalTotal", "300.00",
                "discountCode", "SAVE10"
        );

        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                "/applyDiscount", jsonEntity(body), JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode json = response.getBody();
        assertThat(json).isNotNull();
        assertThat(json.get("orderId").asText()).isEqualTo("ORD-IT-003");
        assertThat(json.get("discountCode").asText()).isEqualTo("SAVE10");
        assertThat(json.get("discountPercent").asDouble()).isCloseTo(10.00, org.assertj.core.data.Offset.offset(0.01));
        assertThat(json.get("discountAmount").asDouble()).isCloseTo(30.00, org.assertj.core.data.Offset.offset(0.01));
        assertThat(json.get("finalTotal").asDouble()).isCloseTo(270.00, org.assertj.core.data.Offset.offset(0.01));
    }

    @Test
    @DisplayName("POST /applyDiscount — unknown code results in zero discount")
    void applyDiscountWithUnknownCodeProducesZeroDiscount() {
        Map<String, Object> body = Map.of(
                "orderId", "ORD-IT-004",
                "originalTotal", "100.00",
                "discountCode", "DOESNOTEXIST"
        );

        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                "/applyDiscount", jsonEntity(body), JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode json = response.getBody();
        assertThat(json).isNotNull();
        assertThat(json.get("discountAmount").asDouble()).isCloseTo(0.00, org.assertj.core.data.Offset.offset(0.001));
        assertThat(json.get("finalTotal").asDouble()).isCloseTo(100.00, org.assertj.core.data.Offset.offset(0.01));
    }

    // =========================================================================
    // POST /generateInvoice
    // =========================================================================

    @Test
    @DisplayName("POST /generateInvoice — full invoice with SAVE20 discount")
    void generateInvoiceWithSave20Discount() {
        // UK order (20% VAT) with SAVE20 (20% off post-tax total)
        // subtotal=100.00, tax=20.00, totalBeforeDiscount=120.00
        // discount=20% of 120.00=24.00, finalTotal=96.00
        Map<String, Object> body = Map.of(
                "orderId", "ORD-IT-005",
                "customerId", "CUST-IT-5",
                "subtotal", "100.00",
                "country", "GB",
                "discountCode", "SAVE20"
        );

        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                "/generateInvoice", jsonEntity(body), JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode json = response.getBody();
        assertThat(json).isNotNull();
        assertThat(json.get("invoiceId").asText()).contains("ORD-IT-005");
        assertThat(json.get("orderId").asText()).isEqualTo("ORD-IT-005");
        assertThat(json.get("customerId").asText()).isEqualTo("CUST-IT-5");
        assertThat(json.get("taxAmount").asDouble()).isCloseTo(20.00, org.assertj.core.data.Offset.offset(0.01));
        assertThat(json.get("totalBeforeDiscount").asDouble()).isCloseTo(120.00, org.assertj.core.data.Offset.offset(0.01));
        assertThat(json.get("discountCode").asText()).isEqualTo("SAVE20");
        assertThat(json.get("discountPercent").asDouble()).isCloseTo(20.00, org.assertj.core.data.Offset.offset(0.01));
        assertThat(json.get("discountAmount").asDouble()).isCloseTo(24.00, org.assertj.core.data.Offset.offset(0.01));
        assertThat(json.get("finalTotal").asDouble()).isCloseTo(96.00, org.assertj.core.data.Offset.offset(0.01));
        assertThat(json.get("issuedAt").asText()).isNotBlank();
    }

    @Test
    @DisplayName("POST /generateInvoice — invoice without discount code")
    void generateInvoiceWithoutDiscount() {
        // Australia (10% GST), no discount
        // subtotal=50.00, tax=5.00, totalBeforeDiscount=55.00, finalTotal=55.00
        Map<String, Object> body = Map.of(
                "orderId", "ORD-IT-006",
                "customerId", "CUST-IT-6",
                "subtotal", "50.00",
                "country", "AU"
        );

        ResponseEntity<JsonNode> response = restTemplate.postForEntity(
                "/generateInvoice", jsonEntity(body), JsonNode.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        JsonNode json = response.getBody();
        assertThat(json).isNotNull();
        assertThat(json.get("taxRate").asDouble()).isCloseTo(0.10, org.assertj.core.data.Offset.offset(0.001));
        assertThat(json.get("taxAmount").asDouble()).isCloseTo(5.00, org.assertj.core.data.Offset.offset(0.01));
        assertThat(json.get("discountAmount").asDouble()).isCloseTo(0.00, org.assertj.core.data.Offset.offset(0.001));
        assertThat(json.get("finalTotal").asDouble()).isCloseTo(55.00, org.assertj.core.data.Offset.offset(0.01));
    }

    // =========================================================================
    // POST /auditLogger
    // =========================================================================

    @Test
    @DisplayName("POST /auditLogger — Consumer returns 202 Accepted")
    void auditLoggerReturns202Accepted() {
        // Consumer beans in the Spring Cloud Function web adapter return 202 Accepted
        // (fire-and-forget: the value is consumed but no response body is produced).
        Map<String, Object> body = Map.of(
                "eventType", "INVOICE_GENERATED",
                "orderId", "ORD-IT-005",
                "actor", "invoice-service",
                "details", "Invoice generated successfully"
        );

        ResponseEntity<Void> response = restTemplate.postForEntity(
                "/auditLogger", jsonEntity(body), Void.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
    }
}
