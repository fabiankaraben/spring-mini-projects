package com.example.autoconfiguration.starter;

import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration class for the custom Greeting starter.
 *
 * <p>This is the heart of a Spring Boot Starter. When Spring Boot starts up, it
 * reads the file:
 * <pre>
 *   META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports
 * </pre>
 * and instantiates each class listed there as a candidate for auto-configuration.
 * This class is listed in that file, so Spring Boot will evaluate it on every startup.
 *
 * <p><b>Annotations explained:</b>
 * <ul>
 *   <li>{@code @AutoConfiguration} — marks this class as an auto-configuration
 *       (replaces the legacy {@code @Configuration} + {@code spring.factories} combo
 *       from Spring Boot 2.x). Spring Boot 3.x uses the {@code .imports} file.</li>
 *
 *   <li>{@code @ConditionalOnProperty(prefix = "greeting", name = "enabled", matchIfMissing = true)}
 *       — the entire auto-configuration is skipped when a user explicitly sets
 *       {@code greeting.enabled=false}. The {@code matchIfMissing = true} means
 *       the condition passes even if the property is not present at all (opt-out
 *       rather than opt-in).</li>
 *
 *   <li>{@code @EnableConfigurationProperties(GreetingProperties.class)} — registers
 *       the {@link GreetingProperties} {@code @ConfigurationProperties} bean into the
 *       application context and binds the {@code greeting.*} properties to it.</li>
 * </ul>
 *
 * <p><b>Bean registration order:</b>
 * <ol>
 *   <li>{@link GreetingProperties} is bound from the environment (YAML / env vars).</li>
 *   <li>{@link GreetingService} is created with those properties, but only if the
 *       application has not already defined its own {@link GreetingService} bean
 *       ({@code @ConditionalOnMissingBean}).</li>
 *   <li>{@link GreetingHealthIndicator} is created only if
 *       {@code spring-boot-starter-actuator} is on the classpath
 *       ({@code @ConditionalOnClass(HealthIndicator.class)}) and only if no
 *       {@link GreetingHealthIndicator} bean already exists.</li>
 * </ol>
 */
@AutoConfiguration
@ConditionalOnProperty(prefix = "greeting", name = "enabled", matchIfMissing = true)
@EnableConfigurationProperties(GreetingProperties.class)
public class GreetingAutoConfiguration {

    /**
     * Registers the {@link GreetingService} bean with the auto-configured properties.
     *
     * <p>{@code @ConditionalOnMissingBean(GreetingService.class)} is the key
     * "back-off" mechanism of auto-configuration: if the application developer
     * has already declared their own {@link GreetingService} bean (e.g. in a
     * {@code @Configuration} class), Spring Boot will skip this factory method
     * entirely. This gives users full control while still providing a sensible
     * default out of the box.
     *
     * @param properties the bound {@link GreetingProperties} (injected by Spring
     *                   from the {@code @EnableConfigurationProperties} declaration)
     * @return the configured {@link GreetingService} instance
     */
    @Bean
    @ConditionalOnMissingBean(GreetingService.class)
    public GreetingService greetingService(GreetingProperties properties) {
        return new GreetingService(properties);
    }

    /**
     * Registers the {@link GreetingHealthIndicator} bean when Actuator is present.
     *
     * <p>{@code @ConditionalOnClass(HealthIndicator.class)} ensures this bean is
     * only created when {@code spring-boot-starter-actuator} is on the classpath.
     * If someone uses this starter without Actuator, the health indicator is simply
     * not registered — no error, no missing-class warning.
     *
     * <p>{@code @ConditionalOnMissingBean(GreetingHealthIndicator.class)} allows the
     * application to override the health indicator with its own implementation.
     *
     * @param greetingService the auto-configured (or user-provided) greeting service
     * @return the health indicator bean
     */
    @Bean
    @ConditionalOnClass(HealthIndicator.class)
    @ConditionalOnMissingBean(GreetingHealthIndicator.class)
    public GreetingHealthIndicator greetingHealthIndicator(GreetingService greetingService) {
        return new GreetingHealthIndicator(greetingService);
    }
}
