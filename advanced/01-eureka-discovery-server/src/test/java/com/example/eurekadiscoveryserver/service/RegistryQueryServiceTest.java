package com.example.eurekadiscoveryserver.service;

import com.example.eurekadiscoveryserver.model.RegistrationSummary;
import com.example.eurekadiscoveryserver.model.ServiceInstance;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link RegistryQueryService}.
 *
 * <p>These tests cover the domain logic in isolation — no Spring context is
 * started, no Eureka server is running. The service is instantiated directly
 * with {@code new}, making each test extremely fast.
 *
 * <p><b>Test strategy:</b>
 * <ul>
 *   <li>Each {@code @Nested} class focuses on one method of the service.</li>
 *   <li>Edge cases (empty list, null appName, all-healthy, all-unhealthy) are
 *       covered alongside happy-path cases.</li>
 *   <li>AssertJ assertions provide readable failure messages.</li>
 * </ul>
 */
@DisplayName("RegistryQueryService")
class RegistryQueryServiceTest {

    /**
     * The service under test. Instantiated directly — no Spring DI needed.
     * A fresh instance is created before each test to avoid any state leakage.
     */
    private RegistryQueryService service;

    @BeforeEach
    void setUp() {
        service = new RegistryQueryService();
    }

    // =========================================================================
    // filterHealthy()
    // =========================================================================

    @Nested
    @DisplayName("filterHealthy()")
    class FilterHealthy {

        @Test
        @DisplayName("returns only UP instances from a mixed list")
        void shouldReturnOnlyUpInstances() {
            // Arrange: two UP, one DOWN, one OUT_OF_SERVICE
            List<ServiceInstance> instances = List.of(
                    ServiceInstance.up("SVC-A", "svc-a:1", "host1", 8080),
                    new ServiceInstance("SVC-B", "svc-b:1", "host2", 8081, "DOWN"),
                    ServiceInstance.up("SVC-A", "svc-a:2", "host3", 8080),
                    new ServiceInstance("SVC-C", "svc-c:1", "host4", 8082, "OUT_OF_SERVICE")
            );

            // Act
            List<ServiceInstance> healthy = service.filterHealthy(instances);

            // Assert: only the two UP instances survive
            assertThat(healthy).hasSize(2);
            assertThat(healthy).allMatch(ServiceInstance::isHealthy);
            assertThat(healthy).extracting(ServiceInstance::appName)
                    .containsExactlyInAnyOrder("SVC-A", "SVC-A");
        }

        @Test
        @DisplayName("returns empty list when no instances are UP")
        void shouldReturnEmptyListWhenNoneAreUp() {
            List<ServiceInstance> instances = List.of(
                    new ServiceInstance("SVC", "svc:1", "host1", 8080, "DOWN"),
                    new ServiceInstance("SVC", "svc:2", "host2", 8080, "STARTING")
            );

            assertThat(service.filterHealthy(instances)).isEmpty();
        }

        @Test
        @DisplayName("returns all instances when all are UP")
        void shouldReturnAllWhenAllAreUp() {
            List<ServiceInstance> instances = List.of(
                    ServiceInstance.up("SVC-A", "svc-a:1", "host1", 8080),
                    ServiceInstance.up("SVC-B", "svc-b:1", "host2", 8081)
            );

            assertThat(service.filterHealthy(instances)).hasSize(2);
        }

        @Test
        @DisplayName("returns empty list when input is empty")
        void shouldReturnEmptyListWhenInputIsEmpty() {
            assertThat(service.filterHealthy(List.of())).isEmpty();
        }
    }

    // =========================================================================
    // filterUnhealthy()
    // =========================================================================

    @Nested
    @DisplayName("filterUnhealthy()")
    class FilterUnhealthy {

        @Test
        @DisplayName("returns only non-UP instances from a mixed list")
        void shouldReturnOnlyNonUpInstances() {
            List<ServiceInstance> instances = List.of(
                    ServiceInstance.up("SVC-A", "svc-a:1", "host1", 8080),
                    new ServiceInstance("SVC-B", "svc-b:1", "host2", 8081, "DOWN"),
                    new ServiceInstance("SVC-C", "svc-c:1", "host3", 8082, "STARTING")
            );

            List<ServiceInstance> unhealthy = service.filterUnhealthy(instances);

            assertThat(unhealthy).hasSize(2);
            assertThat(unhealthy).noneMatch(ServiceInstance::isHealthy);
            assertThat(unhealthy).extracting(ServiceInstance::status)
                    .containsExactlyInAnyOrder("DOWN", "STARTING");
        }

        @Test
        @DisplayName("returns empty list when all instances are UP")
        void shouldReturnEmptyListWhenAllAreUp() {
            List<ServiceInstance> instances = List.of(
                    ServiceInstance.up("SVC-A", "svc-a:1", "host1", 8080),
                    ServiceInstance.up("SVC-B", "svc-b:1", "host2", 8081)
            );

            assertThat(service.filterUnhealthy(instances)).isEmpty();
        }

        @Test
        @DisplayName("returns all instances when none are UP")
        void shouldReturnAllWhenNoneAreUp() {
            List<ServiceInstance> instances = List.of(
                    new ServiceInstance("SVC", "svc:1", "host1", 8080, "DOWN"),
                    new ServiceInstance("SVC", "svc:2", "host2", 8080, "UNKNOWN")
            );

            assertThat(service.filterUnhealthy(instances)).hasSize(2);
        }
    }

    // =========================================================================
    // findByAppName()
    // =========================================================================

    @Nested
    @DisplayName("findByAppName()")
    class FindByAppName {

        @Test
        @DisplayName("returns instances matching the given app name (case-insensitive)")
        void shouldReturnMatchingInstancesCaseInsensitive() {
            List<ServiceInstance> instances = List.of(
                    ServiceInstance.up("ORDER-SERVICE", "order:1", "host1", 8081),
                    ServiceInstance.up("ORDER-SERVICE", "order:2", "host2", 8081),
                    ServiceInstance.up("PAYMENT-SERVICE", "payment:1", "host3", 8082)
            );

            // Query with lowercase — Eureka uses upper-case but our API should be flexible
            List<ServiceInstance> result = service.findByAppName(instances, "order-service");

            assertThat(result).hasSize(2);
            assertThat(result).allMatch(i -> "ORDER-SERVICE".equals(i.appName()));
        }

        @Test
        @DisplayName("returns empty list when no instance matches the app name")
        void shouldReturnEmptyListWhenNoMatchFound() {
            List<ServiceInstance> instances = List.of(
                    ServiceInstance.up("ORDER-SERVICE", "order:1", "host1", 8081)
            );

            assertThat(service.findByAppName(instances, "unknown-service")).isEmpty();
        }

        @Test
        @DisplayName("returns empty list when app name is null")
        void shouldReturnEmptyListWhenAppNameIsNull() {
            List<ServiceInstance> instances = List.of(
                    ServiceInstance.up("ORDER-SERVICE", "order:1", "host1", 8081)
            );

            assertThat(service.findByAppName(instances, null)).isEmpty();
        }

        @Test
        @DisplayName("returns empty list when app name is blank")
        void shouldReturnEmptyListWhenAppNameIsBlank() {
            List<ServiceInstance> instances = List.of(
                    ServiceInstance.up("ORDER-SERVICE", "order:1", "host1", 8081)
            );

            assertThat(service.findByAppName(instances, "   ")).isEmpty();
        }

        @Test
        @DisplayName("returns empty list when instance list is empty")
        void shouldReturnEmptyListWhenInstancesIsEmpty() {
            assertThat(service.findByAppName(List.of(), "ORDER-SERVICE")).isEmpty();
        }
    }

    // =========================================================================
    // buildSummary()
    // =========================================================================

    @Nested
    @DisplayName("buildSummary()")
    class BuildSummary {

        @Test
        @DisplayName("counts applications, total instances, healthy and unhealthy correctly")
        void shouldComputeCorrectCounts() {
            // Arrange: 2 apps, 4 instances total (3 UP, 1 DOWN)
            List<ServiceInstance> instances = List.of(
                    ServiceInstance.up("ORDER-SERVICE", "order:1", "host1", 8081),
                    ServiceInstance.up("ORDER-SERVICE", "order:2", "host2", 8081),
                    ServiceInstance.up("PAYMENT-SERVICE", "payment:1", "host3", 8082),
                    new ServiceInstance("PAYMENT-SERVICE", "payment:2", "host4", 8082, "DOWN")
            );

            // Act
            RegistrationSummary summary = service.buildSummary(instances);

            // Assert
            assertThat(summary.totalApplications()).isEqualTo(2);
            assertThat(summary.totalInstances()).isEqualTo(4);
            assertThat(summary.healthyInstances()).isEqualTo(3);
            assertThat(summary.unhealthyInstances()).isEqualTo(1);
        }

        @Test
        @DisplayName("returns all zeros for an empty registry")
        void shouldReturnZerosForEmptyRegistry() {
            RegistrationSummary summary = service.buildSummary(List.of());

            assertThat(summary.totalApplications()).isZero();
            assertThat(summary.totalInstances()).isZero();
            assertThat(summary.healthyInstances()).isZero();
            assertThat(summary.unhealthyInstances()).isZero();
        }

        @Test
        @DisplayName("counts single app with a single UP instance")
        void shouldHandleSingleInstance() {
            List<ServiceInstance> instances = List.of(
                    ServiceInstance.up("MY-SERVICE", "my-svc:1", "localhost", 9090)
            );

            RegistrationSummary summary = service.buildSummary(instances);

            assertThat(summary.totalApplications()).isEqualTo(1);
            assertThat(summary.totalInstances()).isEqualTo(1);
            assertThat(summary.healthyInstances()).isEqualTo(1);
            assertThat(summary.unhealthyInstances()).isZero();
        }

        @Test
        @DisplayName("isFullyHealthy returns true when all instances are UP")
        void summaryIsFullyHealthyWhenAllUp() {
            List<ServiceInstance> instances = List.of(
                    ServiceInstance.up("SVC-A", "svc-a:1", "host1", 8080),
                    ServiceInstance.up("SVC-B", "svc-b:1", "host2", 8081)
            );

            RegistrationSummary summary = service.buildSummary(instances);

            assertThat(summary.isFullyHealthy()).isTrue();
        }

        @Test
        @DisplayName("isFullyHealthy returns false when at least one instance is not UP")
        void summaryIsNotFullyHealthyWhenAnyUnhealthy() {
            List<ServiceInstance> instances = List.of(
                    ServiceInstance.up("SVC-A", "svc-a:1", "host1", 8080),
                    new ServiceInstance("SVC-B", "svc-b:1", "host2", 8081, "DOWN")
            );

            RegistrationSummary summary = service.buildSummary(instances);

            assertThat(summary.isFullyHealthy()).isFalse();
        }

        @Test
        @DisplayName("isFullyHealthy returns false for empty registry")
        void summaryIsNotFullyHealthyForEmptyRegistry() {
            RegistrationSummary summary = service.buildSummary(List.of());

            assertThat(summary.isFullyHealthy()).isFalse();
        }

        @Test
        @DisplayName("healthRatio returns 1.0 when all instances are UP")
        void healthRatioIsOneWhenAllUp() {
            List<ServiceInstance> instances = List.of(
                    ServiceInstance.up("SVC", "svc:1", "host1", 8080),
                    ServiceInstance.up("SVC", "svc:2", "host2", 8080)
            );

            RegistrationSummary summary = service.buildSummary(instances);

            assertThat(summary.healthRatio()).isEqualTo(1.0);
        }

        @Test
        @DisplayName("healthRatio returns 0.0 when no instances are UP")
        void healthRatioIsZeroWhenNoneUp() {
            List<ServiceInstance> instances = List.of(
                    new ServiceInstance("SVC", "svc:1", "host1", 8080, "DOWN"),
                    new ServiceInstance("SVC", "svc:2", "host2", 8080, "DOWN")
            );

            RegistrationSummary summary = service.buildSummary(instances);

            assertThat(summary.healthRatio()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("healthRatio returns 0.0 when registry is empty")
        void healthRatioIsZeroWhenEmpty() {
            RegistrationSummary summary = service.buildSummary(List.of());

            assertThat(summary.healthRatio()).isEqualTo(0.0);
        }

        @Test
        @DisplayName("healthRatio returns 0.5 when half the instances are UP")
        void healthRatioIsHalfWhenHalfUp() {
            List<ServiceInstance> instances = List.of(
                    ServiceInstance.up("SVC", "svc:1", "host1", 8080),
                    new ServiceInstance("SVC", "svc:2", "host2", 8080, "DOWN")
            );

            RegistrationSummary summary = service.buildSummary(instances);

            assertThat(summary.healthRatio()).isEqualTo(0.5);
        }
    }
}
