package com.example.autoconfiguration.starter;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link GreetingService}.
 *
 * <p><b>Testing approach:</b>
 * <ul>
 *   <li>Pure unit tests — no Spring context, no database, no mocking needed.</li>
 *   <li>{@link GreetingProperties} is instantiated directly and configured
 *       programmatically, exactly as Spring Boot's binding mechanism would do
 *       it from {@code application.yml}.</li>
 *   <li>Tests run in milliseconds because there is no application context to
 *       load — this is the fastest kind of test in the Spring ecosystem.</li>
 *   <li>Uses AssertJ for fluent, readable assertions.</li>
 * </ul>
 *
 * <p><b>What is tested:</b>
 * <ul>
 *   <li>The greeting format: {@code "<prefix>, <name><suffix>"}.</li>
 *   <li>Default-name fallback when null or blank is passed.</li>
 *   <li>Custom prefix and suffix values (simulating different YAML configs).</li>
 *   <li>Configuration summary output used by the health indicator.</li>
 * </ul>
 */
class GreetingServiceTest {

    // Default-configured service reused across tests that verify standard behaviour
    private GreetingService defaultService;

    /**
     * Sets up a {@link GreetingService} with the default property values before
     * each test. Rebuilding it in @BeforeEach ensures complete test isolation —
     * one test cannot affect another by modifying shared state.
     */
    @BeforeEach
    void setUp() {
        // Create a GreetingProperties instance with default values (same as YAML defaults)
        GreetingProperties defaultProps = new GreetingProperties();
        // defaults: prefix="Hello", suffix="!", defaultName="World"

        defaultService = new GreetingService(defaultProps);
    }

    // =========================================================================
    // greet — happy path with a provided name
    // =========================================================================

    @Test
    @DisplayName("greet returns '<prefix>, <name><suffix>' for a provided name")
    void greet_withName_returnsFormattedGreeting() {
        // when
        String result = defaultService.greet("Alice");

        // then — default prefix="Hello", suffix="!"
        assertThat(result).isEqualTo("Hello, Alice!");
    }

    @Test
    @DisplayName("greet with custom prefix and suffix uses configured values")
    void greet_withCustomPrefixAndSuffix_usesConfiguredValues() {
        // given — simulate a different YAML configuration: greeting.prefix=Hi, greeting.suffix=.
        GreetingProperties customProps = new GreetingProperties();
        customProps.setPrefix("Hi");
        customProps.setSuffix(".");
        GreetingService customService = new GreetingService(customProps);

        // when
        String result = customService.greet("Bob");

        // then
        assertThat(result).isEqualTo("Hi, Bob.");
    }

    @Test
    @DisplayName("greet with an empty-string suffix produces no trailing punctuation")
    void greet_withEmptySuffix_producesNoTrailingPunctuation() {
        // given
        GreetingProperties props = new GreetingProperties();
        props.setSuffix("");
        GreetingService service = new GreetingService(props);

        // when
        String result = service.greet("Carol");

        // then
        assertThat(result).isEqualTo("Hello, Carol");
    }

    // =========================================================================
    // greet — null / blank name → fallback to defaultName
    // =========================================================================

    @Test
    @DisplayName("greet with null name falls back to configured defaultName")
    void greet_withNullName_usesDefaultName() {
        // when — null triggers the fallback in GreetingService
        String result = defaultService.greet(null);

        // then — defaultName="World" by default
        assertThat(result).isEqualTo("Hello, World!");
    }

    @Test
    @DisplayName("greet with blank name falls back to configured defaultName")
    void greet_withBlankName_usesDefaultName() {
        // when — blank string also triggers the fallback
        String result = defaultService.greet("   ");

        // then
        assertThat(result).isEqualTo("Hello, World!");
    }

    @Test
    @DisplayName("greet with empty string falls back to configured defaultName")
    void greet_withEmptyString_usesDefaultName() {
        // when
        String result = defaultService.greet("");

        // then
        assertThat(result).isEqualTo("Hello, World!");
    }

    @Test
    @DisplayName("greet uses custom defaultName when configured")
    void greet_withCustomDefaultName_usesCustomDefault() {
        // given — simulate greeting.default-name=Spring in YAML
        GreetingProperties props = new GreetingProperties();
        props.setDefaultName("Spring");
        GreetingService service = new GreetingService(props);

        // when
        String result = service.greet(null);

        // then
        assertThat(result).isEqualTo("Hello, Spring!");
    }

    // =========================================================================
    // greet — all custom properties together
    // =========================================================================

    @Test
    @DisplayName("greet with all custom properties produces correct message")
    void greet_withAllCustomProperties_producesCorrectMessage() {
        // given — simulate a fully customised YAML block
        GreetingProperties props = new GreetingProperties();
        props.setPrefix("Hola");
        props.setSuffix(", bienvenido!");
        props.setDefaultName("Mundo");
        GreetingService service = new GreetingService(props);

        // when
        String result = service.greet("Carlos");

        // then
        assertThat(result).isEqualTo("Hola, Carlos, bienvenido!");
    }

    @Test
    @DisplayName("greet with all custom properties and null name uses custom default")
    void greet_withAllCustomPropertiesAndNullName_usesCustomDefault() {
        // given
        GreetingProperties props = new GreetingProperties();
        props.setPrefix("Bonjour");
        props.setSuffix("!");
        props.setDefaultName("Monde");
        GreetingService service = new GreetingService(props);

        // when
        String result = service.greet(null);

        // then
        assertThat(result).isEqualTo("Bonjour, Monde!");
    }

    // =========================================================================
    // getConfigurationSummary
    // =========================================================================

    @Test
    @DisplayName("getConfigurationSummary returns all three property values")
    void getConfigurationSummary_returnsAllPropertyValues() {
        // when
        String summary = defaultService.getConfigurationSummary();

        // then — must contain all three configured values
        assertThat(summary).contains("prefix='Hello'");
        assertThat(summary).contains("suffix='!'");
        assertThat(summary).contains("defaultName='World'");
    }

    @Test
    @DisplayName("getConfigurationSummary reflects custom property values")
    void getConfigurationSummary_reflectsCustomValues() {
        // given
        GreetingProperties props = new GreetingProperties();
        props.setPrefix("Hey");
        props.setSuffix("?");
        props.setDefaultName("You");
        GreetingService service = new GreetingService(props);

        // when
        String summary = service.getConfigurationSummary();

        // then
        assertThat(summary).contains("prefix='Hey'");
        assertThat(summary).contains("suffix='?'");
        assertThat(summary).contains("defaultName='You'");
    }

    // =========================================================================
    // getProperties — accessor
    // =========================================================================

    @Test
    @DisplayName("getProperties returns the same GreetingProperties instance")
    void getProperties_returnsTheConfiguredProperties() {
        // given
        GreetingProperties props = new GreetingProperties();
        props.setPrefix("Test");
        GreetingService service = new GreetingService(props);

        // when / then — the accessor must return the exact same instance
        assertThat(service.getProperties()).isSameAs(props);
        assertThat(service.getProperties().getPrefix()).isEqualTo("Test");
    }
}
