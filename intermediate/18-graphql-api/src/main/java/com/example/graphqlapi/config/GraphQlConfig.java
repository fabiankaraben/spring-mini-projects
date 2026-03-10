package com.example.graphqlapi.config;

import graphql.scalars.ExtendedScalars;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.graphql.execution.RuntimeWiringConfigurer;

/**
 * GraphQL runtime configuration.
 *
 * <p>Spring for GraphQL builds the executable schema in two phases:
 * <ol>
 *   <li><b>Type definition</b> – reads {@code *.graphqls} files from the classpath
 *       and parses them into a {@code TypeDefinitionRegistry}.</li>
 *   <li><b>Runtime wiring</b> – connects the parsed type definitions with Java
 *       data-fetchers (resolvers) and scalar coercions. This phase is where
 *       custom scalars must be registered.</li>
 * </ol>
 *
 * <p>This configuration class handles the second phase for the custom {@code Date}
 * scalar declared in {@code schema.graphqls}. It uses the
 * {@code graphql-java-extended-scalars} library which ships pre-built coercions
 * for common Java types including {@link java.time.LocalDate}.
 */
@Configuration
public class GraphQlConfig {

    /**
     * Registers custom scalars with the GraphQL runtime wiring.
     *
     * <p>{@link RuntimeWiringConfigurer} is a callback interface that Spring for
     * GraphQL calls when building the {@code RuntimeWiring}. We use it to add the
     * {@code Date} scalar from the extended-scalars library.
     *
     * <p>{@code ExtendedScalars.Date} maps the GraphQL {@code Date} scalar to
     * {@link java.time.LocalDate} in Java. It handles serialisation (LocalDate →
     * ISO-8601 string for the GraphQL response) and deserialisation (ISO-8601
     * string or epoch-day integer → LocalDate for incoming arguments).
     *
     * @return a configurer that adds the {@code Date} scalar to the runtime wiring
     */
    @Bean
    public RuntimeWiringConfigurer runtimeWiringConfigurer() {
        // Add the Date scalar so GraphQL knows how to coerce LocalDate values
        return wiringBuilder -> wiringBuilder.scalar(ExtendedScalars.Date);
    }
}
