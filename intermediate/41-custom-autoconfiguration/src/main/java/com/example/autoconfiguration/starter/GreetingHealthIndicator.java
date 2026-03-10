package com.example.autoconfiguration.starter;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;

/**
 * Custom Actuator {@link HealthIndicator} contributed by the Greeting starter.
 *
 * <p>This class demonstrates how a custom Spring Boot Starter can extend the
 * Spring Boot Actuator health endpoint ({@code /actuator/health}) by registering
 * its own health contributor. When the application context contains this bean,
 * Spring Boot Actuator automatically discovers it (by type) and includes a
 * {@code "greeting"} entry in the health response.
 *
 * <p><b>How it is registered:</b>
 * Spring Boot Actuator discovers all beans that implement {@link HealthIndicator}
 * and exposes them under their bean name (without the "HealthIndicator" suffix).
 * Since this bean is named {@code greetingHealthIndicator}, it appears in
 * {@code /actuator/health} as {@code "greeting"}.
 *
 * <p><b>Example response at {@code /actuator/health}:</b>
 * <pre>{@code
 * {
 *   "status": "UP",
 *   "components": {
 *     "greeting": {
 *       "status": "UP",
 *       "details": {
 *         "configuration": "prefix='Hello', suffix='!', defaultName='World'",
 *         "sample": "Hello, World!"
 *       }
 *     },
 *     "db": { "status": "UP" },
 *     "diskSpace": { "status": "UP" }
 *   }
 * }
 * }</pre>
 *
 * <p>This class is auto-configured in {@link GreetingAutoConfiguration} and is only
 * registered when {@code spring-boot-starter-actuator} is on the classpath
 * (guarded by {@code @ConditionalOnClass(HealthIndicator.class)}).
 */
public class GreetingHealthIndicator implements HealthIndicator {

    /**
     * The auto-configured greeting service whose configuration we report.
     */
    private final GreetingService greetingService;

    /**
     * Constructs the health indicator with the auto-configured {@link GreetingService}.
     *
     * @param greetingService the service to check health for
     */
    public GreetingHealthIndicator(GreetingService greetingService) {
        this.greetingService = greetingService;
    }

    /**
     * Performs the health check for the Greeting starter.
     *
     * <p>As long as the {@link GreetingService} bean exists and can produce a greeting,
     * the status is {@code UP}. If an unexpected exception is thrown, the status is
     * {@code DOWN} with the error details included.
     *
     * @return {@link Health#up()} with configuration details, or
     *         {@link Health#down()} with the exception if something goes wrong
     */
    @Override
    public Health health() {
        try {
            // Attempt to produce a greeting as a smoke-test
            String sample = greetingService.greet(null);

            // Report UP with useful diagnostic details
            return Health.up()
                    .withDetail("configuration", greetingService.getConfigurationSummary())
                    .withDetail("sample", sample)
                    .build();
        } catch (Exception ex) {
            // If greet() throws, something is seriously wrong with the starter config
            return Health.down(ex)
                    .withDetail("message", "GreetingService is not operational")
                    .build();
        }
    }
}
