package com.example.graphqlmutations.config;

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
 *       ({@code src/main/resources/graphql/}) and parses them into a
 *       {@code TypeDefinitionRegistry}.</li>
 *   <li><b>Runtime wiring</b> – connects the parsed type definitions with Java
 *       data-fetchers (resolvers) and scalar coercions. Custom scalars must be
 *       registered in this phase.</li>
 * </ol>
 *
 * <p>This class registers the {@code Long} scalar from the
 * {@code graphql-java-extended-scalars} library, which maps GraphQL {@code Long}
 * to Java's {@code long}/{@link Long}. This is needed because the built-in
 * GraphQL {@code Int} type only supports 32-bit integers, whereas JPA primary
 * keys are typically 64-bit {@code Long} values.
 */
@Configuration
public class GraphQlConfig {

    /**
     * Registers custom scalars with the GraphQL runtime wiring.
     *
     * <p>{@link RuntimeWiringConfigurer} is a callback interface that Spring for
     * GraphQL invokes when building the {@code RuntimeWiring}. We use it to add
     * the {@code Long} scalar from the extended-scalars library.
     *
     * <p>{@code ExtendedScalars.GraphQLLong} maps the GraphQL {@code Long} scalar
     * to Java's {@code Long} type. It handles serialisation (Java Long → JSON number)
     * and deserialisation (JSON number → Java Long) for mutation arguments and
     * query results.
     *
     * @return a configurer that adds the {@code Long} scalar to the runtime wiring
     */
    @Bean
    public RuntimeWiringConfigurer runtimeWiringConfigurer() {
        // Register the Long scalar so GraphQL can coerce Long values for IDs and priorities
        return wiringBuilder -> wiringBuilder.scalar(ExtendedScalars.GraphQLLong);
    }
}
