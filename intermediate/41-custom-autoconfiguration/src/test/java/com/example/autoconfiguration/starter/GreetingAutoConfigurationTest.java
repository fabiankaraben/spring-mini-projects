package com.example.autoconfiguration.starter;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link GreetingAutoConfiguration}.
 *
 * <p><b>Testing approach — ApplicationContextRunner:</b>
 * Spring Boot provides {@link ApplicationContextRunner} specifically for testing
 * auto-configurations without starting a full {@code SpringApplication}. It lets us:
 * <ul>
 *   <li>Apply auto-configurations via {@code withConfiguration(AutoConfigurations.of(...))}.</li>
 *   <li>Override properties via {@code withPropertyValues(...)}.</li>
 *   <li>Register additional beans via {@code withUserConfiguration(...)}.</li>
 *   <li>Assert on the resulting context with {@code run(context -> ...)}.</li>
 * </ul>
 *
 * <p>This is significantly lighter than {@code @SpringBootTest} — no embedded server,
 * no JPA context, no database — making these tests run in under one second.
 *
 * <p><b>What is tested:</b>
 * <ul>
 *   <li>That {@link GreetingService} is registered when no override bean exists.</li>
 *   <li>That {@link GreetingProperties} is correctly bound from property values.</li>
 *   <li>That the {@code @ConditionalOnMissingBean} back-off works: when the application
 *       provides its own {@link GreetingService} bean, the auto-configured one is
 *       NOT registered (the user bean takes precedence).</li>
 *   <li>That {@code greeting.enabled=false} disables the entire auto-configuration.</li>
 *   <li>That the {@link GreetingHealthIndicator} is registered when Actuator is present.</li>
 * </ul>
 */
class GreetingAutoConfigurationTest {

    /**
     * The context runner pre-configured with our auto-configuration.
     *
     * <p>It is immutable and reusable — each {@code run()} call creates a fresh
     * context so tests are fully isolated from each other.
     */
    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(GreetingAutoConfiguration.class));

    // =========================================================================
    // Default auto-configuration — GreetingService is registered
    // =========================================================================

    @Test
    @DisplayName("GreetingService bean is registered with default auto-configuration")
    void greetingService_isRegistered_withDefaultConfig() {
        contextRunner.run(context -> {
            // The auto-configuration must have created the GreetingService bean
            assertThat(context).hasSingleBean(GreetingService.class);
        });
    }

    @Test
    @DisplayName("GreetingProperties bean is registered and bound to default values")
    void greetingProperties_isRegistered_withDefaultValues() {
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(GreetingProperties.class);

            GreetingProperties props = context.getBean(GreetingProperties.class);
            // Verify the default values match what GreetingProperties declares
            assertThat(props.isEnabled()).isTrue();
            assertThat(props.getPrefix()).isEqualTo("Hello");
            assertThat(props.getSuffix()).isEqualTo("!");
            assertThat(props.getDefaultName()).isEqualTo("World");
        });
    }

    @Test
    @DisplayName("GreetingService produces correct greeting with default properties")
    void greetingService_producesCorrectGreeting_withDefaultProperties() {
        contextRunner.run(context -> {
            GreetingService service = context.getBean(GreetingService.class);
            // With defaults: prefix="Hello", suffix="!", defaultName="World"
            assertThat(service.greet("Alice")).isEqualTo("Hello, Alice!");
            assertThat(service.greet(null)).isEqualTo("Hello, World!");
        });
    }

    // =========================================================================
    // Custom property values — @ConfigurationProperties binding
    // =========================================================================

    @Test
    @DisplayName("GreetingProperties are bound from custom property values")
    void greetingProperties_areBound_fromCustomValues() {
        contextRunner
                // Simulate application.yml with custom greeting properties
                .withPropertyValues(
                        "greeting.prefix=Hi",
                        "greeting.suffix=.",
                        "greeting.default-name=Spring"
                )
                .run(context -> {
                    GreetingProperties props = context.getBean(GreetingProperties.class);

                    assertThat(props.getPrefix()).isEqualTo("Hi");
                    assertThat(props.getSuffix()).isEqualTo(".");
                    assertThat(props.getDefaultName()).isEqualTo("Spring");
                });
    }

    @Test
    @DisplayName("GreetingService uses custom properties from YAML binding")
    void greetingService_usesCustomProperties_fromYamlBinding() {
        contextRunner
                .withPropertyValues(
                        "greeting.prefix=Hola",
                        "greeting.suffix=!",
                        "greeting.default-name=Mundo"
                )
                .run(context -> {
                    GreetingService service = context.getBean(GreetingService.class);
                    assertThat(service.greet("Carlos")).isEqualTo("Hola, Carlos!");
                    assertThat(service.greet(null)).isEqualTo("Hola, Mundo!");
                });
    }

    // =========================================================================
    // @ConditionalOnMissingBean — user-defined bean takes precedence
    // =========================================================================

    @Test
    @DisplayName("Auto-configured GreetingService backs off when user provides their own bean")
    void greetingService_backsOff_whenUserBeanExists() {
        // Create a custom GreetingService that the "user" provides
        GreetingProperties customProps = new GreetingProperties();
        customProps.setPrefix("Howdy");
        GreetingService userDefinedService = new GreetingService(customProps);

        contextRunner
                // Simulate the user declaring their own GreetingService @Bean
                .withBean(GreetingService.class, () -> userDefinedService)
                .run(context -> {
                    // There must still be exactly ONE GreetingService bean
                    assertThat(context).hasSingleBean(GreetingService.class);

                    // But it must be the user-defined one, not the auto-configured one
                    GreetingService bean = context.getBean(GreetingService.class);
                    assertThat(bean.greet("Partner")).isEqualTo("Howdy, Partner!");
                });
    }

    // =========================================================================
    // @ConditionalOnProperty — greeting.enabled=false disables everything
    // =========================================================================

    @Test
    @DisplayName("Auto-configuration is disabled when greeting.enabled=false")
    void autoConfiguration_isDisabled_whenEnabledPropertyIsFalse() {
        contextRunner
                // Simulate the user setting greeting.enabled=false in their YAML
                .withPropertyValues("greeting.enabled=false")
                .run(context -> {
                    // Neither the service nor the properties bean should be present
                    assertThat(context).doesNotHaveBean(GreetingService.class);
                    assertThat(context).doesNotHaveBean(GreetingProperties.class);
                });
    }

    @Test
    @DisplayName("Auto-configuration is active when greeting.enabled is absent (matchIfMissing=true)")
    void autoConfiguration_isActive_whenEnabledPropertyIsAbsent() {
        // Do NOT set greeting.enabled — the @ConditionalOnProperty has matchIfMissing=true
        contextRunner.run(context -> {
            // The auto-configuration must be active even without the property
            assertThat(context).hasSingleBean(GreetingService.class);
        });
    }

    @Test
    @DisplayName("Auto-configuration is active when greeting.enabled=true")
    void autoConfiguration_isActive_whenEnabledPropertyIsTrue() {
        contextRunner
                .withPropertyValues("greeting.enabled=true")
                .run(context -> {
                    assertThat(context).hasSingleBean(GreetingService.class);
                });
    }

    // =========================================================================
    // GreetingHealthIndicator — registered when HealthIndicator is on classpath
    // =========================================================================

    @Test
    @DisplayName("GreetingHealthIndicator is registered when Actuator is on classpath")
    void greetingHealthIndicator_isRegistered_whenActuatorIsPresent() {
        // HealthIndicator is on the classpath (spring-boot-starter-actuator is a dep),
        // so the @ConditionalOnClass(HealthIndicator.class) condition passes.
        contextRunner.run(context -> {
            assertThat(context).hasSingleBean(GreetingHealthIndicator.class);

            // Verify the health indicator reports UP
            GreetingHealthIndicator indicator = context.getBean(GreetingHealthIndicator.class);
            assertThat(indicator.health().getStatus().getCode()).isEqualTo("UP");
        });
    }

    @Test
    @DisplayName("GreetingHealthIndicator health details include configuration summary and sample")
    void greetingHealthIndicator_healthDetails_includeConfigAndSample() {
        contextRunner
                .withPropertyValues("greeting.prefix=Hey", "greeting.suffix=?")
                .run(context -> {
                    GreetingHealthIndicator indicator = context.getBean(GreetingHealthIndicator.class);
                    var health = indicator.health();

                    // Both detail keys must be present
                    assertThat(health.getDetails()).containsKey("configuration");
                    assertThat(health.getDetails()).containsKey("sample");

                    // The sample greeting must reflect the custom prefix/suffix
                    String sample = (String) health.getDetails().get("sample");
                    assertThat(sample).startsWith("Hey,");
                    assertThat(sample).endsWith("?");
                });
    }

    @Test
    @DisplayName("GreetingHealthIndicator backs off when user provides their own HealthIndicator bean")
    void greetingHealthIndicator_backsOff_whenUserBeanExists() {
        // Simulate the user providing their own GreetingHealthIndicator
        GreetingProperties props = new GreetingProperties();
        GreetingService svc = new GreetingService(props);
        GreetingHealthIndicator userIndicator = new GreetingHealthIndicator(svc);

        contextRunner
                .withBean(GreetingHealthIndicator.class, () -> userIndicator)
                .run(context -> {
                    // Still only one bean of this type
                    assertThat(context).hasSingleBean(GreetingHealthIndicator.class);
                    // And it must be the user-defined one
                    assertThat(context.getBean(GreetingHealthIndicator.class)).isSameAs(userIndicator);
                });
    }
}
