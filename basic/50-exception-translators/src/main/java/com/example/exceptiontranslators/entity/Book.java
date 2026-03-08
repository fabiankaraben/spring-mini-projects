package com.example.exceptiontranslators.entity;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import java.util.Objects;

/**
 * Entity class representing a Book.
 * This class is mapped to the "books" table in the database.
 * It uses Jakarta Persistence API (JPA) annotations for ORM mapping
 * and Jakarta Validation annotations for data integrity.
 */
@Entity
@Table(name = "books")
public class Book {

    /**
     * Unique identifier for the book.
     * Generated automatically by the database.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * International Standard Book Number.
     * Must not be blank.
     */
    @NotBlank(message = "ISBN is required")
    private String isbn;

    /**
     * Title of the book.
     * Must not be blank.
     */
    @NotBlank(message = "Title is required")
    private String title;

    /**
     * Author of the book.
     * Must not be blank.
     */
    @NotBlank(message = "Author is required")
    private String author;

    /**
     * Default constructor required by JPA.
     */
    public Book() {
    }

    /**
     * Parameterized constructor for creating book instances.
     *
     * @param id     The unique ID
     * @param isbn   The ISBN
     * @param title  The title
     * @param author The author
     */
    public Book(Long id, String isbn, String title, String author) {
        this.id = id;
        this.isbn = isbn;
        this.title = title;
        this.author = author;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getIsbn() {
        return isbn;
    }

    public void setIsbn(String isbn) {
        this.isbn = isbn;
    }

    public String getTitle() {
        return title;
    }

    public void setTitle(String title) {
        this.title = title;
    }

    public String getAuthor() {
        return author;
    }

    public void setAuthor(String author) {
        this.author = author;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        Book book = (Book) o;
        return Objects.equals(id, book.id) && Objects.equals(isbn, book.isbn) && Objects.equals(title, book.title) && Objects.equals(author, book.author);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id, isbn, title, author);
    }

    @Override
    public String toString() {
        return "Book{" +
                "id=" + id +
                ", isbn='" + isbn + '\'' +
                ", title='" + title + '\'' +
                ", author='" + author + '\'' +
                '}';
    }
}
