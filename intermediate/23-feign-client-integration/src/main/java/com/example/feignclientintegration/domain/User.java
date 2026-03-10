package com.example.feignclientintegration.domain;

/**
 * Domain model representing a user from the JSONPlaceholder API.
 *
 * <p>The {@code /users} endpoint returns objects with many fields. We use a
 * record with the fields we care about; Jackson ignores unknown JSON fields by
 * default (or via {@code @JsonIgnoreProperties(ignoreUnknown = true)} if strict
 * deserialization is enabled).
 *
 * @param id       the user's unique identifier
 * @param name     the user's full name
 * @param username the user's login handle
 * @param email    the user's email address
 */
public record User(
        Integer id,
        String name,
        String username,
        String email
) {}
