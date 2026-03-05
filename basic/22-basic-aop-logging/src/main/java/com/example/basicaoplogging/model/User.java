package com.example.basicaoplogging.model;

/**
 * A basic domain record representing a User.
 * 
 * Records are an excellent fit for immutable data carriers in Java 21+.
 * 
 * @param id   The user's unique identifier.
 * @param name The user's full name.
 */
public record User(Long id, String name) {
}
