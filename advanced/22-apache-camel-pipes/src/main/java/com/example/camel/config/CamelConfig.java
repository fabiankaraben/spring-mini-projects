package com.example.camel.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.camel.component.jackson.JacksonDataFormat;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Camel-specific Spring configuration.
 *
 * <p>Spring Boot auto-configures an {@link ObjectMapper} with the {@code JavaTimeModule}
 * already registered (via {@code jackson-datatype-jsr310} on the classpath), enabling
 * serialization of {@code java.time.Instant}, {@code LocalDateTime}, etc.
 *
 * <p>However, the Camel Jackson data format ({@code marshal().json()}) creates its own
 * internal {@link ObjectMapper} by default, which does NOT have {@code JavaTimeModule}.
 * This causes {@code InvalidDefinitionException: Java 8 date/time type not supported}
 * when the pipeline marshals an {@link com.example.camel.domain.Order} that contains
 * {@code Instant} fields ({@code receivedAt}, {@code processedAt}).
 *
 * <p>The fix: expose a {@link JacksonDataFormat} bean that wraps Spring Boot's
 * pre-configured {@link ObjectMapper}. The route uses this bean explicitly instead of
 * the default {@code marshal().json()}.
 */
@Configuration
public class CamelConfig {

    /**
     * A Camel {@link JacksonDataFormat} backed by Spring Boot's auto-configured
     * {@link ObjectMapper}.
     *
     * <p>Spring Boot's {@link ObjectMapper} has the following modules registered:
     * <ul>
     *   <li>{@code JavaTimeModule} — handles {@code java.time.*} types (Instant, etc.)</li>
     *   <li>{@code Jdk8Module}    — handles {@code Optional}, etc.</li>
     *   <li>{@code ParameterNamesModule} — enables constructor injection without @JsonProperty</li>
     * </ul>
     *
     * <p>By injecting this bean as a named bean called {@code "orderJsonFormat"}, the route
     * can use {@code .marshal("orderJsonFormat")} to pick it up by name from the Camel registry.
     *
     * @param objectMapper Spring Boot's auto-configured ObjectMapper (injected by Spring).
     * @return A JacksonDataFormat that uses the shared, fully-configured ObjectMapper.
     */
    @Bean
    public JacksonDataFormat orderJsonFormat(ObjectMapper objectMapper) {
        // Wrap Spring Boot's ObjectMapper so all its registered modules (including
        // JavaTimeModule for Instant serialization) are available inside the Camel route.
        JacksonDataFormat format = new JacksonDataFormat(objectMapper, Object.class);
        // prettyPrint=false keeps JSON compact for JMS and file output.
        format.setPrettyPrint(false);
        return format;
    }
}
