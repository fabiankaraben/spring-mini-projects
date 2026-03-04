package com.example.basicrouter.model;

/**
 * Represents a course in the system.
 * We are using Java Records (introduced in Java 14) which gives us a
 * compact syntax for declaring data classes with immutable fields.
 */
public record Course(Long id, String title, String description) {
}
