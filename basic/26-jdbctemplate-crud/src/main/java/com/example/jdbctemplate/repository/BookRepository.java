package com.example.jdbctemplate.repository;

import com.example.jdbctemplate.domain.Book;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.List;
import java.util.Optional;

/**
 * Repository class that interacts with the database using JdbcTemplate.
 * It provides basic CRUD operations for the Book entity.
 */
@Repository
public class BookRepository {

    private final JdbcTemplate jdbcTemplate;

    // Inject JdbcTemplate provided by Spring Boot Auto-configuration
    public BookRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    // RowMapper maps a single row of the ResultSet to a Book object
    private final RowMapper<Book> bookRowMapper = (rs, rowNum) -> {
        Book book = new Book();
        book.setId(rs.getLong("id"));
        book.setTitle(rs.getString("title"));
        book.setAuthor(rs.getString("author"));
        book.setPublishedYear(rs.getInt("published_year"));
        return book;
    };

    /**
     * Retrieves all books from the database.
     */
    public List<Book> findAll() {
        String sql = "SELECT * FROM books";
        return jdbcTemplate.query(sql, bookRowMapper);
    }

    /**
     * Retrieves a book by its ID.
     */
    public Optional<Book> findById(Long id) {
        String sql = "SELECT * FROM books WHERE id = ?";
        List<Book> books = jdbcTemplate.query(sql, bookRowMapper, id);
        return books.stream().findFirst();
    }

    /**
     * Inserts a new book into the database.
     * Uses KeyHolder to retrieve the auto-generated ID.
     */
    public Book save(Book book) {
        String sql = "INSERT INTO books (title, author, published_year) VALUES (?, ?, ?)";
        KeyHolder keyHolder = new GeneratedKeyHolder();

        // Using PreparedStatementCreator to be able to fetch the generated keys
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, book.getTitle());
            ps.setString(2, book.getAuthor());
            ps.setInt(3, book.getPublishedYear());
            return ps;
        }, keyHolder);

        // Retrieve the generated ID and set it to the book
        if (keyHolder.getKeys() != null && keyHolder.getKeys().containsKey("id")) {
            Number id = (Number) keyHolder.getKeys().get("id");
            book.setId(id.longValue());
        }
        return book;
    }

    /**
     * Updates an existing book.
     */
    public int update(Book book) {
        String sql = "UPDATE books SET title = ?, author = ?, published_year = ? WHERE id = ?";
        return jdbcTemplate.update(sql, book.getTitle(), book.getAuthor(), book.getPublishedYear(), book.getId());
    }

    /**
     * Deletes a book by its ID.
     */
    public int deleteById(Long id) {
        String sql = "DELETE FROM books WHERE id = ?";
        return jdbcTemplate.update(sql, id);
    }
}
