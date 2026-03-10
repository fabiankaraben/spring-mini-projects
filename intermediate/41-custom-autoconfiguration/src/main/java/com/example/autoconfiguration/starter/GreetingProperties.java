package com.example.autoconfiguration.starter;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Typed configuration properties for the custom Greeting starter.
 *
 * <p>{@code @ConfigurationProperties(prefix = "greeting")} binds every property
 * whose key starts with {@code greeting.} in {@code application.yml} (or environment
 * variables) to the fields of this class. This is strongly preferred over raw
 * {@code @Value} because:
 * <ul>
 *   <li>All starter properties are grouped in one cohesive object.</li>
 *   <li>The Spring Boot Configuration Processor annotation processor generates
 *       {@code META-INF/spring-configuration-metadata.json} from this class so
 *       IDEs can provide auto-complete and documentation for custom properties.</li>
 *   <li>Validation annotations (JSR-380) can be applied to the fields.</li>
 * </ul>
 *
 * <p><b>Supported properties in {@code application.yml}:</b>
 * <pre>{@code
 * greeting:
 *   enabled: true          # Set to false to disable auto-configuration entirely
 *   prefix: "Hello"        # Greeting word prepended to every name
 *   suffix: "!"            # Punctuation appended after the name
 *   default-name: "World"  # Fallback name when none is supplied by the caller
 * }</pre>
 *
 * <p>This class is registered as a bean in {@link GreetingAutoConfiguration}
 * via {@code @EnableConfigurationProperties(GreetingProperties.class)}.
 */
@ConfigurationProperties(prefix = "greeting")
public class GreetingProperties {

    /**
     * Whether the Greeting auto-configuration is enabled.
     * Set to {@code false} to completely disable the auto-configured beans.
     * Default: {@code true}.
     */
    private boolean enabled = true;

    /**
     * Word or phrase prepended before the name in each greeting message.
     * Example: "Hello" produces "Hello, Alice!".
     * Default: {@code "Hello"}.
     */
    private String prefix = "Hello";

    /**
     * Punctuation or text appended after the name in each greeting message.
     * Example: "!" produces "Hello, Alice!".
     * Default: {@code "!"}.
     */
    private String suffix = "!";

    /**
     * Name used when the caller does not supply one.
     * Default: {@code "World"}.
     */
    private String defaultName = "World";

    // -------------------------------------------------------------------------
    // Getters and setters — required by Spring Boot's binding mechanism.
    // Spring uses JavaBeans conventions (setXxx) to inject the values.
    // -------------------------------------------------------------------------

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getPrefix() {
        return prefix;
    }

    public void setPrefix(String prefix) {
        this.prefix = prefix;
    }

    public String getSuffix() {
        return suffix;
    }

    public void setSuffix(String suffix) {
        this.suffix = suffix;
    }

    public String getDefaultName() {
        return defaultName;
    }

    public void setDefaultName(String defaultName) {
        this.defaultName = defaultName;
    }
}
