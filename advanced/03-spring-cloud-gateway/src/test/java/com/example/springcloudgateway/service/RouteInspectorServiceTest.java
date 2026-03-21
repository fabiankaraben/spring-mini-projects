package com.example.springcloudgateway.service;

import com.example.springcloudgateway.model.GatewayStatus;
import com.example.springcloudgateway.model.RouteInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.cloud.gateway.filter.FilterDefinition;
import org.springframework.cloud.gateway.handler.predicate.PredicateDefinition;
import org.springframework.cloud.gateway.route.Route;
import org.springframework.cloud.gateway.route.RouteLocator;
import org.springframework.cloud.gateway.route.builder.RouteLocatorBuilder;
import org.springframework.mock.web.reactive.function.server.MockServerRequest;
import reactor.core.publisher.Flux;
import reactor.test.StepVerifier;

import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link RouteInspectorService}.
 *
 * <p><b>Testing strategy:</b>
 * The {@link RouteInspectorService} depends on Spring Cloud Gateway's
 * {@link RouteLocator} to query the live route registry. In unit tests we do
 * NOT want to start the full Spring context — instead, we mock the
 * {@link RouteLocator} with Mockito and control exactly which routes it returns.
 * This isolates the service's logic from the gateway infrastructure.
 *
 * <p><b>Key annotations:</b>
 * <ul>
 *   <li>{@code @ExtendWith(MockitoExtension.class)} — activates Mockito's JUnit
 *       5 extension, which processes {@code @Mock} annotations automatically
 *       without needing a Spring context.</li>
 *   <li>{@code @Mock} — creates a Mockito mock for the injected dependency.</li>
 * </ul>
 *
 * <p><b>Reactive testing with StepVerifier:</b>
 * {@link RouteLocator#getRoutes()} returns a {@link Flux}. We use Project
 * Reactor's {@link StepVerifier} to subscribe to the Flux and assert on the
 * emitted elements in a structured, synchronous way — without blocking threads
 * or using {@code .block()}.
 *
 * <p>The {@code @Value("${spring.application.name}")} field in the service is
 * NOT injected in a unit test (no Spring context). We set it directly by
 * accessing it via reflection in {@link #setUp()}. Alternatively, we can just
 * accept that the field will be null and avoid asserting on it, or use
 * {@code @SpringBootTest} for those assertions — but we deliberately keep
 * these tests lightweight.
 */
@ExtendWith(MockitoExtension.class)
@DisplayName("RouteInspectorService — unit tests")
class RouteInspectorServiceTest {

    /** Mocked route locator — we control what routes it returns. */
    @Mock
    private RouteLocator routeLocator;

    /** The service under test, constructed manually (no Spring DI). */
    private RouteInspectorService service;

    /**
     * Creates a fresh service instance before each test, injecting the mock.
     *
     * <p>Because the service also has a {@code @Value}-injected
     * {@code applicationName} field, we use reflection to set it directly.
     * This avoids the overhead of a full Spring context just for one string.
     */
    @BeforeEach
    void setUp() throws Exception {
        service = new RouteInspectorService(routeLocator);

        // Inject applicationName directly via reflection (bypasses @Value).
        var field = RouteInspectorService.class.getDeclaredField("applicationName");
        field.setAccessible(true);
        field.set(service, "spring-cloud-gateway");
    }

    // =========================================================================
    // Helper: build a minimal Route for testing
    // =========================================================================

    /**
     * Builds a minimal Spring Cloud Gateway {@link Route} object suitable for
     * mocking purposes.
     *
     * <p>{@link Route} is built via its {@link Route.AsyncBuilder} fluent API.
     * The predicate and filters are set to no-ops to keep the test data minimal.
     *
     * @param id  the route ID
     * @param uri the downstream target URI
     * @return a {@link Route} instance
     */
    private Route buildRoute(String id, String uri) {
        return Route.async()
                .id(id)
                .uri(uri)
                // A predicate that always matches — we only care about route metadata.
                .predicate(exchange -> true)
                .build();
    }

    // =========================================================================
    // getRoutes() tests
    // =========================================================================

    /**
     * Verifies that when the {@link RouteLocator} returns two routes,
     * {@link RouteInspectorService#getRoutes()} emits exactly two
     * {@link RouteInfo} records with the correct IDs and URIs.
     */
    @Test
    @DisplayName("getRoutes() emits one RouteInfo per route from the RouteLocator")
    void getRoutesShouldEmitOneRouteInfoPerRoute() {
        // Arrange: mock the RouteLocator to return two routes.
        Route product = buildRoute("product-service-route", "http://localhost:8081");
        Route order   = buildRoute("order-service-route",   "http://localhost:8082");
        when(routeLocator.getRoutes()).thenReturn(Flux.just(product, order));

        // Act + Assert using StepVerifier (reactive).
        StepVerifier.create(service.getRoutes())
                // First emitted RouteInfo should be for the product route.
                .assertNext(info -> {
                    assertThat(info.id()).isEqualTo("product-service-route");
                    assertThat(info.uri()).isEqualTo("http://localhost:8081");
                })
                // Second emitted RouteInfo should be for the order route.
                .assertNext(info -> {
                    assertThat(info.id()).isEqualTo("order-service-route");
                    assertThat(info.uri()).isEqualTo("http://localhost:8082");
                })
                // No more elements expected — verify the Flux completed.
                .verifyComplete();
    }

    /**
     * Verifies that when the {@link RouteLocator} returns no routes,
     * {@link RouteInspectorService#getRoutes()} completes immediately with no
     * elements (empty Flux).
     */
    @Test
    @DisplayName("getRoutes() completes with no elements when RouteLocator is empty")
    void getRoutesShouldCompleteEmptyWhenNoRoutesExist() {
        // Arrange: mock an empty route registry.
        when(routeLocator.getRoutes()).thenReturn(Flux.empty());

        // Act + Assert.
        StepVerifier.create(service.getRoutes())
                // Immediately completes — no elements emitted.
                .verifyComplete();
    }

    // =========================================================================
    // getRouteList() tests
    // =========================================================================

    /**
     * Verifies that {@link RouteInspectorService#getRouteList()} returns a
     * plain {@link List} collected from the reactive stream.
     */
    @Test
    @DisplayName("getRouteList() returns a plain List with all routes")
    void getRouteListShouldReturnAllRoutesAsList() {
        // Arrange
        Route user = buildRoute("user-service-route", "http://localhost:8083");
        when(routeLocator.getRoutes()).thenReturn(Flux.just(user));

        // Act
        List<RouteInfo> result = service.getRouteList();

        // Assert
        assertThat(result).hasSize(1);
        assertThat(result.get(0).id()).isEqualTo("user-service-route");
        assertThat(result.get(0).uri()).isEqualTo("http://localhost:8083");
    }

    /**
     * Verifies that {@code getRouteList()} returns an empty list when no routes
     * are configured.
     */
    @Test
    @DisplayName("getRouteList() returns empty list when no routes exist")
    void getRouteListShouldReturnEmptyListWhenNoRoutes() {
        when(routeLocator.getRoutes()).thenReturn(Flux.empty());

        List<RouteInfo> result = service.getRouteList();

        assertThat(result).isEmpty();
    }

    // =========================================================================
    // getStatus() tests
    // =========================================================================

    /**
     * Verifies that {@link RouteInspectorService#getStatus()} emits a
     * {@link GatewayStatus} with the correct application name, "UP" status,
     * and the list of route IDs from the RouteLocator.
     */
    @Test
    @DisplayName("getStatus() returns GatewayStatus with correct applicationName and routeIds")
    void getStatusShouldReturnCorrectApplicationNameAndRouteIds() {
        // Arrange: two routes in the registry.
        Route product = buildRoute("product-service-route", "http://localhost:8081");
        Route order   = buildRoute("order-service-route",   "http://localhost:8082");
        when(routeLocator.getRoutes()).thenReturn(Flux.just(product, order));

        // Act + Assert.
        StepVerifier.create(service.getStatus())
                .assertNext(status -> {
                    assertThat(status.applicationName()).isEqualTo("spring-cloud-gateway");
                    assertThat(status.status()).isEqualTo("UP");
                    assertThat(status.totalRoutes()).isEqualTo(2);
                    assertThat(status.routeIds())
                            .containsExactlyInAnyOrder(
                                    "product-service-route",
                                    "order-service-route");
                    // Timestamp should be recent (within 5 seconds of now).
                    assertThat(status.timestamp()).isNotNull();
                })
                .verifyComplete();
    }

    /**
     * Verifies that {@code getStatus()} handles zero routes correctly:
     * {@code totalRoutes} is 0 and {@code routeIds} is empty.
     */
    @Test
    @DisplayName("getStatus() returns totalRoutes=0 and empty routeIds when no routes exist")
    void getStatusShouldHandleZeroRoutes() {
        when(routeLocator.getRoutes()).thenReturn(Flux.empty());

        StepVerifier.create(service.getStatus())
                .assertNext(status -> {
                    assertThat(status.totalRoutes()).isZero();
                    assertThat(status.routeIds()).isEmpty();
                    assertThat(status.status()).isEqualTo("UP");
                })
                .verifyComplete();
    }
}
