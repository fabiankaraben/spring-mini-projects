package com.example.autoconfiguration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Custom Auto-Configuration demo application.
 *
 * <p>This application demonstrates how to build a custom Spring Boot Starter
 * by shipping auto-configured beans alongside a {@code @ConfigurationProperties}
 * class, a conditional registration mechanism, and a health indicator.
 *
 * <p><b>Key concepts shown in this project:</b>
 * <ul>
 *   <li>{@code @AutoConfiguration} — marks a class as an auto-configuration
 *       candidate that Spring Boot discovers via the
 *       {@code META-INF/spring/org.springframework.boot.autoconfigure.AutoConfiguration.imports}
 *       file (the replacement for the legacy {@code spring.factories} file).</li>
 *   <li>{@code @ConfigurationProperties} — binds typed configuration values from
 *       {@code application.yml} (e.g. {@code greeting.prefix}) without raw
 *       {@code @Value} lookups.</li>
 *   <li>{@code @ConditionalOnMissingBean} — lets application developers override the
 *       auto-configured bean simply by declaring their own bean of the same type.</li>
 *   <li>{@code @ConditionalOnProperty} — disables the entire auto-configuration when
 *       {@code greeting.enabled=false}, showing opt-out capability.</li>
 *   <li>{@code HealthIndicator} — the starter contributes a custom entry to the
 *       Spring Boot Actuator {@code /actuator/health} endpoint.</li>
 * </ul>
 *
 * <p><b>Package layout:</b>
 * <pre>
 *   com.example.autoconfiguration
 *   ├── CustomAutoConfigurationApplication.java  ← this class (Spring Boot entry point)
 *   │
 *   ├── starter/                                 ← custom starter library code
 *   │   ├── GreetingProperties.java              ← @ConfigurationProperties bean
 *   │   ├── GreetingService.java                 ← the auto-configured service
 *   │   ├── GreetingHealthIndicator.java         ← Actuator health indicator
 *   │   └── GreetingAutoConfiguration.java       ← @AutoConfiguration class
 *   │
 *   ├── entity/
 *   │   └── GreetingLog.java                     ← JPA entity (persists each greeting)
 *   ├── repository/
 *   │   └── GreetingLogRepository.java           ← Spring Data JPA repository
 *   ├── service/
 *   │   └── GreetingLogService.java              ← application service (uses GreetingService)
 *   ├── controller/
 *   │   └── GreetingController.java              ← REST API
 *   ├── dto/
 *   │   ├── GreetingRequest.java
 *   │   └── GreetingResponse.java
 *   └── exception/
 *       ├── GreetingLogNotFoundException.java
 *       └── GlobalExceptionHandler.java
 * </pre>
 */
@SpringBootApplication
public class CustomAutoConfigurationApplication {

    public static void main(String[] args) {
        SpringApplication.run(CustomAutoConfigurationApplication.class, args);
    }
}
