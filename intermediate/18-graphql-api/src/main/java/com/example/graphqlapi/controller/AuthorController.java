package com.example.graphqlapi.controller;

import com.example.graphqlapi.domain.Author;
import com.example.graphqlapi.dto.AuthorInput;
import com.example.graphqlapi.service.AuthorService;
import jakarta.validation.Valid;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.util.List;

/**
 * GraphQL controller (resolver) for {@link Author}-related queries and mutations.
 *
 * <p>In Spring for GraphQL, {@code @Controller}-annotated classes handle GraphQL
 * operations. The mapping annotations correspond directly to fields in the GraphQL
 * schema:
 * <ul>
 *   <li>{@link QueryMapping} – maps a method to a field on the root {@code Query} type.</li>
 *   <li>{@link MutationMapping} – maps a method to a field on the root {@code Mutation} type.</li>
 *   <li>{@link Argument} – binds a GraphQL argument value to a method parameter.
 *       Spring for GraphQL deserialises the input automatically.</li>
 * </ul>
 *
 * <p>This controller is intentionally thin: it validates input, delegates to the
 * {@link AuthorService}, and returns the result. All business logic lives in the service.
 */
@Controller
public class AuthorController {

    private final AuthorService authorService;

    public AuthorController(AuthorService authorService) {
        this.authorService = authorService;
    }

    // ── Query handlers ────────────────────────────────────────────────────────────

    /**
     * Resolves the {@code authors} query – returns all authors.
     *
     * <p>GraphQL schema:
     * <pre>{@code
     * type Query {
     *   authors: [Author!]!
     * }
     * }</pre>
     *
     * @return list of all authors
     */
    @QueryMapping
    public List<Author> authors() {
        return authorService.findAll();
    }

    /**
     * Resolves the {@code author(id: ID!)} query – returns one author by ID.
     *
     * <p>GraphQL schema:
     * <pre>{@code
     * type Query {
     *   author(id: ID!): Author
     * }
     * }</pre>
     *
     * <p>Returns {@code null} if no author with the given ID exists, which GraphQL
     * serialises as a {@code null} field value (allowed because the return type is nullable).
     *
     * @param id the GraphQL {@code ID} argument, bound as a {@link Long}
     * @return the author, or {@code null} if not found
     */
    @QueryMapping
    public Author author(@Argument Long id) {
        return authorService.findById(id).orElse(null);
    }

    /**
     * Resolves the {@code searchAuthors(name: String!)} query.
     *
     * @param name the name fragment to search for
     * @return list of matching authors
     */
    @QueryMapping
    public List<Author> searchAuthors(@Argument String name) {
        return authorService.searchByName(name);
    }

    // ── Mutation handlers ─────────────────────────────────────────────────────────

    /**
     * Resolves the {@code createAuthor(input: AuthorInput!)} mutation.
     *
     * <p>The {@code @Valid} annotation triggers Bean Validation on the input object
     * before the method body executes. If validation fails, Spring for GraphQL
     * returns a structured error response automatically.
     *
     * @param input the deserialized GraphQL {@code AuthorInput} argument
     * @return the newly created author with its generated ID
     */
    @MutationMapping
    public Author createAuthor(@Argument @Valid AuthorInput input) {
        return authorService.create(input);
    }

    /**
     * Resolves the {@code updateAuthor(id: ID!, input: AuthorInput!)} mutation.
     *
     * @param id    the ID of the author to update
     * @param input the new field values
     * @return the updated author, or {@code null} if not found
     */
    @MutationMapping
    public Author updateAuthor(@Argument Long id, @Argument @Valid AuthorInput input) {
        return authorService.update(id, input).orElse(null);
    }

    /**
     * Resolves the {@code deleteAuthor(id: ID!)} mutation.
     *
     * @param id the ID of the author to delete
     * @return {@code true} if deleted successfully; {@code false} if not found
     */
    @MutationMapping
    public boolean deleteAuthor(@Argument Long id) {
        return authorService.deleteById(id);
    }
}
