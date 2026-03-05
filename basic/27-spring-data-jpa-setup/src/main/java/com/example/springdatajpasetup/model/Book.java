package com.example.springdatajpasetup.model;

import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/**
 * The Book entity maps to a table named "books" in the database.
 * The @Entity annotation indicates that this class is a JPA entity.
 * This object will hold the records of books inside the H2 database.
 */
@Entity
@Table(name = "books")
public class Book {

    /**
     * Primary key for the book entity.
     * GenerationType.IDENTITY means the database will auto-increment this ID.
     */
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Title of the book
    private String title;

    // Author of the book
    private String author;

    // Price of the book
    private Double price;

    // Default constructor is required by JPA
    public Book() {
    }

    // Parameterized constructor for easy instantiation
    public Book(String title, String author, Double price) {
        this.title = title;
        this.author = author;
        this.price = price;
    }

    // Getters and setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public Double getPrice() {
        return price;
    }

    public void setPrice(Double price) {
        this.price = price;
    }
}
