package com.example.resttemplate.dto;

/**
 * Represents a Post entity from the JSONPlaceholder API.
 * We use a Java Record to simply and concisely define the data carrier.
 */
public record Post(Long userId, Long id, String title, String body) {
}
