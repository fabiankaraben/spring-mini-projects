package com.example.webclient_basic.dto;

/**
 * A record to hold the basic JSON returned by JSONPlaceholder users API.
 * This is an educational DTO to wrap external API responses.
 */
public record User(
        Long id,
        String name,
        String username,
        String email
) {
}
