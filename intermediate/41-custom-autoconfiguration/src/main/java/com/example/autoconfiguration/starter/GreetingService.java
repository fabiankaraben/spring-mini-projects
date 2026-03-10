package com.example.autoconfiguration.starter;

/**
 * The core service provided by the custom Greeting starter.
 *
 * <p>This bean is auto-configured by {@link GreetingAutoConfiguration} when
 * the following conditions are met:
 * <ol>
 *   <li>{@code greeting.enabled} is {@code true} (or absent — defaults to true).</li>
 *   <li>No other bean of type {@link GreetingService} already exists in the
 *       application context (the {@code @ConditionalOnMissingBean} guard in the
 *       auto-configuration class allows application developers to override this
 *       default implementation with their own).</li>
 * </ol>
 *
 * <p><b>Design note:</b> In a real-world multi-module starter (separate JAR), this
 * class would live in its own Maven module (e.g. {@code greeting-spring-boot-starter}).
 * Here it is kept in the {@code starter} sub-package to demonstrate all the concepts
 * in a single module without complicating the build structure.
 */
public class GreetingService {

    /**
     * The properties bound from {@code application.yml} under the {@code greeting.*}
     * prefix. Injected by the auto-configuration class via the constructor.
     */
    private final GreetingProperties properties;

    /**
     * Constructs a {@link GreetingService} with the given configuration properties.
     *
     * <p>The auto-configuration class passes the {@link GreetingProperties} bean here,
     * making the service fully configured from external YAML without any hard-coded values.
     *
     * @param properties the externalized configuration for this service
     */
    public GreetingService(GreetingProperties properties) {
        this.properties = properties;
    }

    /**
     * Generates a greeting message for the given name.
     *
     * <p>The message is constructed from the configured {@code prefix}, the provided
     * {@code name}, and the configured {@code suffix}. If {@code name} is blank or null,
     * the configured {@code defaultName} is used instead.
     *
     * <p>Example output with defaults ({@code prefix="Hello"}, {@code suffix="!"}):
     * <ul>
     *   <li>{@code greet("Alice")} → {@code "Hello, Alice!"}</li>
     *   <li>{@code greet(null)}   → {@code "Hello, World!"}</li>
     *   <li>{@code greet("")}     → {@code "Hello, World!"}</li>
     * </ul>
     *
     * @param name the person's name to greet; may be null or blank
     * @return the formatted greeting string
     */
    public String greet(String name) {
        // Use the configured default name if none is supplied
        String effectiveName = (name == null || name.isBlank())
                ? properties.getDefaultName()
                : name;

        // Build the message: "<prefix>, <name><suffix>"
        return properties.getPrefix() + ", " + effectiveName + properties.getSuffix();
    }

    /**
     * Returns a summary of the current configuration for diagnostic purposes.
     *
     * <p>Used by the {@link GreetingHealthIndicator} to include configuration
     * details in the Actuator health response.
     *
     * @return a human-readable configuration summary
     */
    public String getConfigurationSummary() {
        return String.format(
                "prefix='%s', suffix='%s', defaultName='%s'",
                properties.getPrefix(),
                properties.getSuffix(),
                properties.getDefaultName()
        );
    }

    /**
     * Exposes the underlying properties for use by other components (e.g. tests).
     *
     * @return the {@link GreetingProperties} this service was configured with
     */
    public GreetingProperties getProperties() {
        return properties;
    }
}
