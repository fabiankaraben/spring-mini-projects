package com.example.feignclientintegration.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Request DTO for creating a new post via the JSONPlaceholder API.
 *
 * <p>This DTO is validated at the controller layer (via {@code @Valid}) before
 * being forwarded to the service and ultimately to the Feign client. Bean
 * Validation annotations ensure the upstream POST request carries valid data.
 *
 * <p>JSONPlaceholder's POST /posts endpoint accepts JSON with these fields
 * and returns the created post with a server-assigned {@code id}.
 *
 * @param userId the ID of the user authoring the post (must be ≥ 1)
 * @param title  the post title (must not be blank)
 * @param body   the post body text (must not be blank)
 */
public record CreatePostRequest(

        /** The ID of the user who will author this post. */
        @NotNull(message = "userId must not be null")
        @Min(value = 1, message = "userId must be at least 1")
        Integer userId,

        /** A short, descriptive title for the post. */
        @NotBlank(message = "title must not be blank")
        String title,

        /** The main content of the post. */
        @NotBlank(message = "body must not be blank")
        String body
) {}
