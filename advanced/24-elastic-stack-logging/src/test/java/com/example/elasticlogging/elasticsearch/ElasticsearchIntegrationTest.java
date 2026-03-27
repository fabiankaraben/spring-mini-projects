package com.example.elasticlogging.elasticsearch;

import com.example.elasticlogging.dto.CreateOrderRequest;
import com.example.elasticlogging.model.Order;
import com.example.elasticlogging.service.OrderService;
import org.apache.http.HttpHost;
import org.elasticsearch.client.Request;
import org.elasticsearch.client.Response;
import org.elasticsearch.client.RestClient;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.testcontainers.elasticsearch.ElasticsearchContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration test demonstrating that the Elastic Stack logging pipeline
 * works end-to-end with a real Elasticsearch instance.
 *
 * <h2>What this test does</h2>
 * <ol>
 *   <li>Starts a real Elasticsearch container via Testcontainers.</li>
 *   <li>Indexes a sample log document directly into Elasticsearch using the
 *       low-level REST client (simulating what Logstash would do in production).</li>
 *   <li>Queries Elasticsearch via the REST API to verify that the document
 *       is searchable by its structured fields.</li>
 *   <li>Also exercises the full Spring context to verify that the
 *       {@link OrderService} business logic is correct under the test profile.</li>
 * </ol>
 *
 * <h2>Why we use the low-level REST client</h2>
 * The high-level Elasticsearch Java client requires strict version alignment
 * between the client library and the server. Using the low-level REST client
 * (org.elasticsearch.client:elasticsearch-rest-client) avoids version conflicts
 * and works against any Elasticsearch 7.x/8.x server.
 *
 * <h2>Testcontainers setup</h2>
 * {@code @Testcontainers} enables JUnit 5 integration. {@code @Container} marks
 * the static field as the container to manage. Using a static container means
 * the same Elasticsearch instance is reused across all test methods in this class,
 * avoiding costly start/stop cycles.
 *
 * <h2>Container image</h2>
 * We use the official {@link ElasticsearchContainer} from Testcontainers with
 * Elasticsearch 8.x (security disabled for tests). On ARM64 (Apple Silicon)
 * this image is multi-arch and works natively.
 */
@SpringBootTest
@ActiveProfiles("test")
@Testcontainers
@DisplayName("Elasticsearch integration tests")
class ElasticsearchIntegrationTest {

    /**
     * Elasticsearch container managed by Testcontainers.
     *
     * <p>Static field + @Container ensures it is started once for the test class
     * and shared across all test methods — much faster than per-method containers.
     *
     * <p>withEnv("xpack.security.enabled", "false") disables TLS/authentication
     * which is required in Elasticsearch 8.x by default but unnecessary for tests.
     */
    @Container
    static ElasticsearchContainer elasticsearch = new ElasticsearchContainer(
            "docker.elastic.co/elasticsearch/elasticsearch:8.13.0")
            .withEnv("xpack.security.enabled", "false")
            .withEnv("discovery.type", "single-node");

    /**
     * Low-level Elasticsearch REST client used to index and query documents.
     * Initialised in @BeforeAll after the container has started.
     */
    static RestClient restClient;

    /** Spring-managed OrderService — exercises the application layer. */
    @Autowired
    OrderService orderService;

    // =========================================================================
    // Lifecycle
    // =========================================================================

    /**
     * Initialises the REST client after the Elasticsearch container has started.
     * The container URL is only available after start-up, so we cannot use a
     * static initialiser.
     */
    @BeforeAll
    static void setUpRestClient() {
        // elasticsearch.getHttpHostAddress() returns "localhost:<mapped-port>"
        restClient = RestClient.builder(
                HttpHost.create(elasticsearch.getHttpHostAddress())
        ).build();
    }

    /**
     * Closes the REST client after all tests have run to release network resources.
     */
    @AfterAll
    static void tearDownRestClient() throws IOException {
        if (restClient != null) {
            restClient.close();
        }
    }

    // =========================================================================
    // Tests
    // =========================================================================

    @Test
    @DisplayName("Elasticsearch container starts and is reachable via REST")
    void elasticsearchContainer_isRunningAndReachable() throws IOException {
        // Send a GET / request to Elasticsearch — the cluster info endpoint.
        // A successful 200 response confirms the container is up and the REST API works.
        Request request = new Request("GET", "/");
        Response response = restClient.performRequest(request);

        // Assert — HTTP 200 means Elasticsearch is healthy and the API is available
        assertThat(response.getStatusLine().getStatusCode()).isEqualTo(200);

        // Optionally log the cluster info to console (useful during development)
        String body = new String(
                response.getEntity().getContent().readAllBytes(), StandardCharsets.UTF_8);
        assertThat(body).contains("cluster_name");
    }

    @Test
    @DisplayName("Log document can be indexed and searched in Elasticsearch")
    void logDocument_canBeIndexedAndSearched() throws IOException, InterruptedException {
        // -----------------------------------------------------------------------
        // Step 1: Index a sample structured log document
        //
        // In production this would be done by Logstash after receiving events
        // from Filebeat. Here we do it directly to test the Elasticsearch layer
        // in isolation from the log-shipping pipeline.
        // -----------------------------------------------------------------------
        String indexName = "app-logs-test";
        String documentId = "test-log-001";

        // This JSON mimics what logstash-logback-encoder produces for an INFO event
        // with StructuredArguments.kv("orderId", "ord-123") and MDC requestId.
        String logDocument = """
                {
                  "@timestamp": "2024-01-15T10:30:00.000Z",
                  "@version": "1",
                  "message": "Order created",
                  "logger_name": "com.example.elasticlogging.service.OrderService",
                  "thread_name": "http-nio-8080-exec-1",
                  "level": "INFO",
                  "level_value": 20000,
                  "application": "elastic-stack-logging",
                  "orderId": "ord-123",
                  "customerId": "customer-001",
                  "amount": "1299.99",
                  "status": "PENDING",
                  "requestId": "req-abc-456"
                }
                """;

        // Index the document using PUT /<index>/_doc/<id>
        // Using PUT with an explicit ID is idempotent — safe to run multiple times.
        Request indexRequest = new Request("PUT",
                "/" + indexName + "/_doc/" + documentId);
        indexRequest.setJsonEntity(logDocument);
        Response indexResponse = restClient.performRequest(indexRequest);

        // Assert indexing succeeded (200 = updated, 201 = created)
        assertThat(indexResponse.getStatusLine().getStatusCode())
                .isIn(200, 201);

        // -----------------------------------------------------------------------
        // Step 2: Refresh the index so the document is immediately searchable
        //
        // Elasticsearch indexes documents asynchronously by default. Calling
        // _refresh forces an immediate flush so the next search returns the
        // newly-indexed document.
        // -----------------------------------------------------------------------
        Request refreshRequest = new Request("POST", "/" + indexName + "/_refresh");
        restClient.performRequest(refreshRequest);

        // -----------------------------------------------------------------------
        // Step 3: Search for the document by the structured "orderId" field
        //
        // This verifies that Elasticsearch correctly parsed the JSON document
        // and that structured fields (orderId, customerId, etc.) are searchable.
        // -----------------------------------------------------------------------
        String searchQuery = """
                {
                  "query": {
                    "term": {
                      "orderId.keyword": "ord-123"
                    }
                  }
                }
                """;

        Request searchRequest = new Request("GET", "/" + indexName + "/_search");
        searchRequest.setJsonEntity(searchQuery);
        Response searchResponse = restClient.performRequest(searchRequest);

        String searchBody = new String(
                searchResponse.getEntity().getContent().readAllBytes(),
                StandardCharsets.UTF_8);

        // Assert — the search result must contain our document
        assertThat(searchResponse.getStatusLine().getStatusCode()).isEqualTo(200);
        assertThat(searchBody).contains("ord-123");
        assertThat(searchBody).contains("Order created");
        assertThat(searchBody).contains("customer-001");
    }

    @Test
    @DisplayName("Multiple log documents with different levels can be indexed and filtered")
    void multipleLogDocuments_canBeFilteredByLevel() throws IOException {
        // -----------------------------------------------------------------------
        // Index three log documents at different severity levels
        // -----------------------------------------------------------------------
        String indexName = "app-logs-levels-test";

        indexLogDocument(indexName, "doc-info",
                "INFO", "Order created", "ord-200");
        indexLogDocument(indexName, "doc-warn",
                "WARN", "Order not found", "ord-404");
        indexLogDocument(indexName, "doc-error",
                "ERROR", "Order processing failed", "ord-500");

        // Refresh so all documents are immediately visible in searches
        restClient.performRequest(new Request("POST", "/" + indexName + "/_refresh"));

        // -----------------------------------------------------------------------
        // Query only ERROR-level log events — simulates a Kibana alert query
        // -----------------------------------------------------------------------
        String errorQuery = """
                {
                  "query": {
                    "term": {
                      "level.keyword": "ERROR"
                    }
                  }
                }
                """;

        Request searchRequest = new Request("GET", "/" + indexName + "/_search");
        searchRequest.setJsonEntity(errorQuery);
        Response searchResponse = restClient.performRequest(searchRequest);

        String responseBody = new String(
                searchResponse.getEntity().getContent().readAllBytes(),
                StandardCharsets.UTF_8);

        // Assert — only the ERROR document should be returned
        assertThat(responseBody).contains("ERROR");
        assertThat(responseBody).contains("Order processing failed");
        // INFO and WARN documents must NOT appear in the ERROR-filtered result
        assertThat(responseBody).doesNotContain("Order created");
        assertThat(responseBody).doesNotContain("Order not found");
    }

    @Test
    @DisplayName("OrderService domain logic works correctly under test profile")
    void orderService_domainLogicWorksUnderTestProfile() {
        // -----------------------------------------------------------------------
        // Verify the Spring application context + OrderService is healthy.
        // This test also exercises all the structured logging code paths in
        // OrderService (createOrder, getOrder, updateOrderStatus) without
        // asserting on log output — the compilation and runtime correctness
        // of the logging calls is what we validate here.
        // -----------------------------------------------------------------------

        // Arrange & Act — create an order
        CreateOrderRequest request = new CreateOrderRequest(
                "es-test-customer", "Elasticsearch node", new BigDecimal("5000.00"));
        Order order = orderService.createOrder(request);

        // Assert creation
        assertThat(order.getId()).isNotBlank();
        assertThat(order.getStatus()).isEqualTo(Order.Status.PENDING);

        // Act — update status (exercises the status-transition log path)
        Order updated = orderService.updateOrderStatus(order.getId(), Order.Status.PROCESSING);

        // Assert update
        assertThat(updated.getStatus()).isEqualTo(Order.Status.PROCESSING);
        assertThat(updated.getUpdatedAt()).isNotNull();

        // Act — retrieve order (exercises the getOrder log path)
        Order retrieved = orderService.getOrder(order.getId());
        assertThat(retrieved.getId()).isEqualTo(order.getId());
    }

    // =========================================================================
    // Helpers
    // =========================================================================

    /**
     * Helper to index a minimal log document into Elasticsearch.
     *
     * @param indexName  target index name
     * @param documentId unique ID for this document
     * @param level      log level string (INFO, WARN, ERROR)
     * @param message    log message string
     * @param orderId    order ID structured field value
     */
    private void indexLogDocument(String indexName, String documentId,
                                  String level, String message, String orderId)
            throws IOException {

        String document = String.format("""
                {
                  "@timestamp": "2024-01-15T10:30:00.000Z",
                  "@version": "1",
                  "message": "%s",
                  "level": "%s",
                  "application": "elastic-stack-logging",
                  "orderId": "%s"
                }
                """, message, level, orderId);

        Request request = new Request("PUT",
                "/" + indexName + "/_doc/" + documentId);
        request.setJsonEntity(document);
        restClient.performRequest(request);
    }
}
