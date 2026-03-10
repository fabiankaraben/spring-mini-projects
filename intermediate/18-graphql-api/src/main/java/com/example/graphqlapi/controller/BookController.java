package com.example.graphqlapi.controller;

import com.example.graphqlapi.domain.Book;
import com.example.graphqlapi.dto.BookInput;
import com.example.graphqlapi.service.BookService;
import jakarta.validation.Valid;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.MutationMapping;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.stereotype.Controller;

import java.util.List;

/**
 * GraphQL controller (resolver) for {@link Book}-related queries and mutations.
 *
 * <p>Maps methods to fields defined in the GraphQL schema's root {@code Query}
 * and {@code Mutation} types. Spring for GraphQL scans for {@code @Controller}
 * beans and wires their annotated methods as field resolvers using the method
 * name as the default field name (overridable via annotation attributes).
 *
 * <p>The controller is kept thin — it performs only argument binding and delegates
 * to {@link BookService} for all business logic.
 */
@Controller
public class BookController {

    private final BookService bookService;

    public BookController(BookService bookService) {
        this.bookService = bookService;
    }

    // ── Query handlers ────────────────────────────────────────────────────────────

    /**
     * Resolves the {@code books} query – returns all books.
     *
     * <p>GraphQL schema:
     * <pre>{@code
     * type Query {
     *   books: [Book!]!
     * }
     * }</pre>
     *
     * @return list of all books
     */
    @QueryMapping
    public List<Book> books() {
        return bookService.findAll();
    }

    /**
     * Resolves the {@code book(id: ID!)} query – returns one book by ID.
     *
     * @param id the book's primary key
     * @return the book, or {@code null} if not found
     */
    @QueryMapping
    public Book book(@Argument Long id) {
        return bookService.findById(id).orElse(null);
    }

    /**
     * Resolves the {@code booksByGenre(genre: String!)} query.
     *
     * @param genre the genre name to filter by
     * @return list of books in that genre
     */
    @QueryMapping
    public List<Book> booksByGenre(@Argument String genre) {
        return bookService.findByGenre(genre);
    }

    /**
     * Resolves the {@code booksByAuthor(authorId: ID!)} query.
     *
     * @param authorId the ID of the author whose books to retrieve
     * @return list of books by the given author
     */
    @QueryMapping
    public List<Book> booksByAuthor(@Argument Long authorId) {
        return bookService.findByAuthorId(authorId);
    }

    /**
     * Resolves the {@code searchBooks(title: String!)} query.
     *
     * @param title the title fragment to search for
     * @return list of matching books
     */
    @QueryMapping
    public List<Book> searchBooks(@Argument String title) {
        return bookService.searchByTitle(title);
    }

    // ── Mutation handlers ─────────────────────────────────────────────────────────

    /**
     * Resolves the {@code createBook(input: BookInput!)} mutation.
     *
     * @param input the deserialized GraphQL {@code BookInput} argument
     * @return the newly created book with its generated ID
     */
    @MutationMapping
    public Book createBook(@Argument @Valid BookInput input) {
        return bookService.create(input);
    }

    /**
     * Resolves the {@code updateBook(id: ID!, input: BookInput!)} mutation.
     *
     * @param id    the ID of the book to update
     * @param input the new field values
     * @return the updated book, or {@code null} if not found
     */
    @MutationMapping
    public Book updateBook(@Argument Long id, @Argument @Valid BookInput input) {
        return bookService.update(id, input).orElse(null);
    }

    /**
     * Resolves the {@code deleteBook(id: ID!)} mutation.
     *
     * @param id the ID of the book to delete
     * @return {@code true} if deleted; {@code false} if not found
     */
    @MutationMapping
    public boolean deleteBook(@Argument Long id) {
        return bookService.deleteById(id);
    }
}
