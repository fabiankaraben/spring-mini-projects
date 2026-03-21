package com.example.eurekadiscoveryserver.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the {@link ServiceInstance} domain value object.
 *
 * <p>These tests verify the business logic methods on the record:
 * {@link ServiceInstance#isHealthy()}, {@link ServiceInstance#baseUrl()},
 * and the {@link ServiceInstance#up(String, String, String, int)} factory method.
 *
 * <p><b>Why unit tests here?</b>
 * {@link ServiceInstance} is a pure Java record with no Spring or Eureka
 * dependencies. Unit tests run without any application context, making them
 * extremely fast and reliable. They document the expected behaviour of the
 * domain model.
 *
 * <p><b>Test conventions used:</b>
 * <ul>
 *   <li>{@code @Nested} groups logically related test cases into inner classes.</li>
 *   <li>{@code @DisplayName} provides human-readable descriptions in test reports.</li>
 *   <li>AssertJ's {@code assertThat()} is used for fluent, readable assertions.</li>
 * </ul>
 */
@DisplayName("ServiceInstance domain model")
class ServiceInstanceTest {

    // -------------------------------------------------------------------------
    // Factory method
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("up() factory method")
    class UpFactoryMethod {

        @Test
        @DisplayName("creates an instance with status UP")
        void shouldCreateInstanceWithStatusUp() {
            // Arrange & Act: use the convenience factory
            ServiceInstance instance = ServiceInstance.up(
                    "ORDER-SERVICE", "order-service:8081", "host1", 8081);

            // Assert: all fields are set correctly
            assertThat(instance.appName()).isEqualTo("ORDER-SERVICE");
            assertThat(instance.instanceId()).isEqualTo("order-service:8081");
            assertThat(instance.hostName()).isEqualTo("host1");
            assertThat(instance.port()).isEqualTo(8081);
            assertThat(instance.status()).isEqualTo("UP");
        }
    }

    // -------------------------------------------------------------------------
    // isHealthy()
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("isHealthy()")
    class IsHealthy {

        @Test
        @DisplayName("returns true when status is UP")
        void shouldBeTrueWhenStatusIsUp() {
            ServiceInstance instance = new ServiceInstance(
                    "SVC", "svc:8080", "localhost", 8080, "UP");
            assertThat(instance.isHealthy()).isTrue();
        }

        @Test
        @DisplayName("returns true when status is up (case-insensitive)")
        void shouldBeTrueForLowerCaseUp() {
            // Eureka normalises to upper-case, but our method should be resilient
            ServiceInstance instance = new ServiceInstance(
                    "SVC", "svc:8080", "localhost", 8080, "up");
            assertThat(instance.isHealthy()).isTrue();
        }

        @Test
        @DisplayName("returns false when status is DOWN")
        void shouldBeFalseWhenStatusIsDown() {
            ServiceInstance instance = new ServiceInstance(
                    "SVC", "svc:8080", "localhost", 8080, "DOWN");
            assertThat(instance.isHealthy()).isFalse();
        }

        @Test
        @DisplayName("returns false when status is STARTING")
        void shouldBeFalseWhenStatusIsStarting() {
            ServiceInstance instance = new ServiceInstance(
                    "SVC", "svc:8080", "localhost", 8080, "STARTING");
            assertThat(instance.isHealthy()).isFalse();
        }

        @Test
        @DisplayName("returns false when status is OUT_OF_SERVICE")
        void shouldBeFalseWhenStatusIsOutOfService() {
            ServiceInstance instance = new ServiceInstance(
                    "SVC", "svc:8080", "localhost", 8080, "OUT_OF_SERVICE");
            assertThat(instance.isHealthy()).isFalse();
        }

        @Test
        @DisplayName("returns false when status is UNKNOWN")
        void shouldBeFalseWhenStatusIsUnknown() {
            ServiceInstance instance = new ServiceInstance(
                    "SVC", "svc:8080", "localhost", 8080, "UNKNOWN");
            assertThat(instance.isHealthy()).isFalse();
        }
    }

    // -------------------------------------------------------------------------
    // baseUrl()
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("baseUrl()")
    class BaseUrl {

        @Test
        @DisplayName("builds correct HTTP URL from hostname and port")
        void shouldBuildCorrectUrl() {
            ServiceInstance instance = ServiceInstance.up(
                    "PAYMENT-SERVICE", "payment:9090", "payment-host", 9090);

            // Expect format: http://<hostname>:<port>
            assertThat(instance.baseUrl()).isEqualTo("http://payment-host:9090");
        }

        @Test
        @DisplayName("works with IP address as hostname")
        void shouldWorkWithIpAddress() {
            ServiceInstance instance = new ServiceInstance(
                    "SVC", "svc:8080", "192.168.1.42", 8080, "UP");

            assertThat(instance.baseUrl()).isEqualTo("http://192.168.1.42:8080");
        }

        @Test
        @DisplayName("works with port 80")
        void shouldWorkWithPort80() {
            ServiceInstance instance = new ServiceInstance(
                    "SVC", "svc:80", "api.example.com", 80, "UP");

            assertThat(instance.baseUrl()).isEqualTo("http://api.example.com:80");
        }
    }

    // -------------------------------------------------------------------------
    // Record equality
    // -------------------------------------------------------------------------

    @Nested
    @DisplayName("record equality and immutability")
    class Equality {

        @Test
        @DisplayName("two instances with the same fields are equal")
        void shouldBeEqualWhenFieldsMatch() {
            ServiceInstance a = new ServiceInstance(
                    "SVC", "svc:8080", "localhost", 8080, "UP");
            ServiceInstance b = new ServiceInstance(
                    "SVC", "svc:8080", "localhost", 8080, "UP");

            // Java records auto-generate equals() and hashCode() based on all components
            assertThat(a).isEqualTo(b);
            assertThat(a).hasSameHashCodeAs(b);
        }

        @Test
        @DisplayName("two instances with different status are not equal")
        void shouldNotBeEqualWhenStatusDiffers() {
            ServiceInstance up = new ServiceInstance(
                    "SVC", "svc:8080", "localhost", 8080, "UP");
            ServiceInstance down = new ServiceInstance(
                    "SVC", "svc:8080", "localhost", 8080, "DOWN");

            assertThat(up).isNotEqualTo(down);
        }
    }
}
