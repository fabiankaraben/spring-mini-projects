package com.example.feignclientintegration.domain;

/**
 * Domain model representing a blog post from the JSONPlaceholder API.
 *
 * <p>JSONPlaceholder is a free fake REST API for testing and prototyping.
 * The {@code /posts} endpoint returns objects with these fields.
 *
 * <p>This record acts as both the Feign response model (deserialized from the
 * upstream API's JSON) and the response body returned to our API consumers.
 * In a real application you might use separate DTOs for upstream vs downstream
 * if the shapes differ.
 *
 * @param id     the post's unique identifier (assigned by JSONPlaceholder)
 * @param userId the ID of the user who authored this post
 * @param title  a short title describing the post
 * @param body   the main content of the post
 */
public record Post(
        Integer id,
        Integer userId,
        String title,
        String body
) {}
