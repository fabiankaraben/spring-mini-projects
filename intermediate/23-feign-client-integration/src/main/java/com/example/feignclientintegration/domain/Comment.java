package com.example.feignclientintegration.domain;

/**
 * Domain model representing a comment from the JSONPlaceholder API.
 *
 * <p>The {@code /comments} endpoint returns objects with these fields.
 * Comments are associated with posts via the {@code postId} foreign key.
 *
 * @param id     the comment's unique identifier
 * @param postId the ID of the post this comment belongs to
 * @param name   a short name/subject for the comment
 * @param email  the email address of the commenter
 * @param body   the text content of the comment
 */
public record Comment(
        Integer id,
        Integer postId,
        String name,
        String email,
        String body
) {}
