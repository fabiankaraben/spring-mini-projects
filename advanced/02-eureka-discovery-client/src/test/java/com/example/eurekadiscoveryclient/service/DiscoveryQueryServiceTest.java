package com.example.eurekadiscoveryclient.service;

import com.example.eurekadiscoveryclient.model.RegistrationStatus;
import com.example.eurekadiscoveryclient.model.ServiceInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link DiscoveryQueryService}.
 *
 * <p>These tests cover the domain logic in isolation — no Spring context is
 * started, no Eureka server is running. The service is instantiated directly
 * with {@code new}, making each test extremely fast.
 *
 * <p><b>Test strategy:</b>
 * <ul>
 *   <li>Each {@code @Nested} class focuses on one method of the service.</li>
 *   <li>Edge cases (empty list, null service ID, blank service ID) are covered
 *       alongside happy-path scenarios.</li>
 *   <li>AssertJ assertions provide readable failure messages.</li>
 * </ul>
 */
@DisplayName("DiscoveryQueryService")
class DiscoveryQueryServiceTest {

    /**
     * The service under test. Instantiated directly — no Spring DI needed.
     * A fresh instance is created before each test to avoid any state leakage.
     */
    private DiscoveryQueryService service;

    @BeforeEach
    void setUp() {
        service = new DiscoveryQueryService();
    }

    // =========================================================================
    // findByServiceId()
    // =========================================================================

    @Nested
    @DisplayName("findByServiceId()")
    class FindByServiceId {

        @Test
        @DisplayName("returns instances matching the given service ID (case-insensitive)")
        void shouldReturnMatchingInstancesCaseInsensitive() {
            // Arrange: two instances of ORDER-SERVICE, one of PAYMENT-SERVICE
            List<ServiceInfo> services = List.of(
                    ServiceInfo.of("ORDER-SERVICE", "order:1", "host1", 8081),
                    ServiceInfo.of("ORDER-SERVICE", "order:2", "host2", 8081),
                    ServiceInfo.of("PAYMENT-SERVICE", "payment:1", "host3", 8082)
            );

            // Act: query with lowercase — Eureka uses upper-case but our API is flexible
            List<ServiceInfo> result = service.findByServiceId(services, "order-service");

            // Assert: only the two ORDER-SERVICE instances are returned
            assertThat(result).hasSize(2);
            assertThat(result).allMatch(s -> "ORDER-SERVICE".equals(s.serviceId()));
        }

        @Test
        @DisplayName("returns empty list when no instance matches the service ID")
        void shouldReturnEmptyListWhenNoMatchFound() {
            List<ServiceInfo> services = List.of(
                    ServiceInfo.of("ORDER-SERVICE", "order:1", "host1", 8081)
            );

            assertThat(service.findByServiceId(services, "unknown-service")).isEmpty();
        }

        @Test
        @DisplayName("returns empty list when service ID is null")
        void shouldReturnEmptyListWhenServiceIdIsNull() {
            List<ServiceInfo> services = List.of(
                    ServiceInfo.of("ORDER-SERVICE", "order:1", "host1", 8081)
            );

            assertThat(service.findByServiceId(services, null)).isEmpty();
        }

        @Test
        @DisplayName("returns empty list when service ID is blank")
        void shouldReturnEmptyListWhenServiceIdIsBlank() {
            List<ServiceInfo> services = List.of(
                    ServiceInfo.of("ORDER-SERVICE", "order:1", "host1", 8081)
            );

            assertThat(service.findByServiceId(services, "   ")).isEmpty();
        }

        @Test
        @DisplayName("returns empty list when the services list is empty")
        void shouldReturnEmptyListWhenServicesIsEmpty() {
            assertThat(service.findByServiceId(List.of(), "ORDER-SERVICE")).isEmpty();
        }

        @Test
        @DisplayName("returns all instances when all share the same service ID")
        void shouldReturnAllWhenAllMatchServiceId() {
            List<ServiceInfo> services = List.of(
                    ServiceInfo.of("SVC", "svc:1", "host1", 8080),
                    ServiceInfo.of("SVC", "svc:2", "host2", 8080),
                    ServiceInfo.of("SVC", "svc:3", "host3", 8080)
            );

            List<ServiceInfo> result = service.findByServiceId(services, "SVC");

            assertThat(result).hasSize(3);
        }
    }

    // =========================================================================
    // countDistinctServices()
    // =========================================================================

    @Nested
    @DisplayName("countDistinctServices()")
    class CountDistinctServices {

        @Test
        @DisplayName("counts correctly with multiple instances of the same service")
        void shouldCountDistinctServiceIdsCorrectly() {
            // Arrange: ORDER-SERVICE has 2 instances, PAYMENT-SERVICE has 1 — 2 distinct
            List<ServiceInfo> services = List.of(
                    ServiceInfo.of("ORDER-SERVICE",   "order:1",   "host1", 8081),
                    ServiceInfo.of("ORDER-SERVICE",   "order:2",   "host2", 8081),
                    ServiceInfo.of("PAYMENT-SERVICE", "payment:1", "host3", 8082)
            );

            // Act
            int count = service.countDistinctServices(services);

            // Assert: 2 distinct service IDs
            assertThat(count).isEqualTo(2);
        }

        @Test
        @DisplayName("returns 0 for an empty list")
        void shouldReturnZeroForEmptyList() {
            assertThat(service.countDistinctServices(List.of())).isZero();
        }

        @Test
        @DisplayName("returns 1 when all instances share the same service ID")
        void shouldReturnOneWhenAllSameServiceId() {
            List<ServiceInfo> services = List.of(
                    ServiceInfo.of("SVC", "svc:1", "host1", 8080),
                    ServiceInfo.of("SVC", "svc:2", "host2", 8080)
            );

            assertThat(service.countDistinctServices(services)).isEqualTo(1);
        }

        @Test
        @DisplayName("treats mixed-case service IDs as the same service")
        void shouldTreatMixedCaseAsSameService() {
            // Eureka normalises to upper-case but our domain model should be tolerant
            List<ServiceInfo> services = List.of(
                    new ServiceInfo("order-service", "order:1", "host1", 8081, "http://host1:8081"),
                    new ServiceInfo("ORDER-SERVICE", "order:2", "host2", 8081, "http://host2:8081")
            );

            // Both are the same logical service — count should be 1
            assertThat(service.countDistinctServices(services)).isEqualTo(1);
        }

        @Test
        @DisplayName("returns N when N distinct service IDs are present")
        void shouldReturnNForNDistinctServiceIds() {
            List<ServiceInfo> services = List.of(
                    ServiceInfo.of("SVC-A", "svc-a:1", "h1", 8001),
                    ServiceInfo.of("SVC-B", "svc-b:1", "h2", 8002),
                    ServiceInfo.of("SVC-C", "svc-c:1", "h3", 8003)
            );

            assertThat(service.countDistinctServices(services)).isEqualTo(3);
        }
    }

    // =========================================================================
    // buildStatus()
    // =========================================================================

    @Nested
    @DisplayName("buildStatus()")
    class BuildStatus {

        @Test
        @DisplayName("creates a RegistrationStatus with all provided values")
        void shouldCreateStatusWithAllValues() {
            // Act
            RegistrationStatus status = service.buildStatus("product-service", 3, true, true);

            // Assert: all fields match the supplied arguments
            assertThat(status.applicationName()).isEqualTo("product-service");
            assertThat(status.registeredServices()).isEqualTo(3);
            assertThat(status.registrationEnabled()).isTrue();
            assertThat(status.fetchEnabled()).isTrue();
        }

        @Test
        @DisplayName("creates a fully inactive status when both flags are false")
        void shouldCreateInactiveStatus() {
            RegistrationStatus status = service.buildStatus("test-service", 0, false, false);

            assertThat(status.isFullyActive()).isFalse();
            assertThat(status.hasDiscoveredServices()).isFalse();
        }

        @Test
        @DisplayName("creates a fully active status when both flags are true and services found")
        void shouldCreateFullyActiveStatus() {
            RegistrationStatus status = service.buildStatus("product-service", 2, true, true);

            assertThat(status.isFullyActive()).isTrue();
            assertThat(status.hasDiscoveredServices()).isTrue();
        }

        @Test
        @DisplayName("preserves zero registeredServices count")
        void shouldPreserveZeroCount() {
            RegistrationStatus status = service.buildStatus("svc", 0, true, true);

            assertThat(status.registeredServices()).isZero();
            assertThat(status.hasDiscoveredServices()).isFalse();
        }
    }

    // =========================================================================
    // sortByServiceId()
    // =========================================================================

    @Nested
    @DisplayName("sortByServiceId()")
    class SortByServiceId {

        @Test
        @DisplayName("returns instances sorted alphabetically by service ID")
        void shouldSortAlphabeticallyByServiceId() {
            // Arrange: intentionally out of order
            List<ServiceInfo> services = List.of(
                    ServiceInfo.of("ZEBRA-SERVICE",   "z:1", "host3", 9003),
                    ServiceInfo.of("ALPHA-SERVICE",   "a:1", "host1", 9001),
                    ServiceInfo.of("MIDDLE-SERVICE",  "m:1", "host2", 9002)
            );

            // Act
            List<ServiceInfo> sorted = service.sortByServiceId(services);

            // Assert: sorted alphabetically
            assertThat(sorted).extracting(ServiceInfo::serviceId)
                    .containsExactly("ALPHA-SERVICE", "MIDDLE-SERVICE", "ZEBRA-SERVICE");
        }

        @Test
        @DisplayName("returns empty list when input is empty")
        void shouldReturnEmptyListForEmptyInput() {
            assertThat(service.sortByServiceId(List.of())).isEmpty();
        }

        @Test
        @DisplayName("returns single-element list unchanged")
        void shouldReturnSingleElementUnchanged() {
            List<ServiceInfo> services = List.of(
                    ServiceInfo.of("ONLY-SERVICE", "only:1", "host1", 8080)
            );

            assertThat(service.sortByServiceId(services))
                    .extracting(ServiceInfo::serviceId)
                    .containsExactly("ONLY-SERVICE");
        }

        @Test
        @DisplayName("groups instances of the same service ID together")
        void shouldGroupSameServiceIdTogether() {
            // Arrange: instances of the same service mixed between different services
            List<ServiceInfo> services = List.of(
                    ServiceInfo.of("BETA-SERVICE",  "b:1", "host3", 8083),
                    ServiceInfo.of("ALPHA-SERVICE", "a:1", "host1", 8081),
                    ServiceInfo.of("BETA-SERVICE",  "b:2", "host4", 8084),
                    ServiceInfo.of("ALPHA-SERVICE", "a:2", "host2", 8082)
            );

            List<ServiceInfo> sorted = service.sortByServiceId(services);

            // Assert: ALPHA-SERVICE instances come before BETA-SERVICE instances
            assertThat(sorted).extracting(ServiceInfo::serviceId)
                    .containsExactly(
                            "ALPHA-SERVICE", "ALPHA-SERVICE",
                            "BETA-SERVICE",  "BETA-SERVICE"
                    );
        }

        @Test
        @DisplayName("sort is case-insensitive")
        void shouldSortCaseInsensitive() {
            List<ServiceInfo> services = List.of(
                    new ServiceInfo("zebra-svc", "z:1", "h3", 8003, "http://h3:8003"),
                    new ServiceInfo("ALPHA-SVC", "a:1", "h1", 8001, "http://h1:8001")
            );

            List<ServiceInfo> sorted = service.sortByServiceId(services);

            // "ALPHA-SVC" < "zebra-svc" case-insensitively
            assertThat(sorted.get(0).serviceId()).isEqualTo("ALPHA-SVC");
            assertThat(sorted.get(1).serviceId()).isEqualTo("zebra-svc");
        }
    }
}
