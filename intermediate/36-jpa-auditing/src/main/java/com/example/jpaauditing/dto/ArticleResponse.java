package com.example.jpaauditing.dto;

import com.example.jpaauditing.entity.Article;

import java.time.Instant;

/**
 * Outbound DTO (Data Transfer Object) returned by the REST API for every
 * article-related response.
 *
 * <p>This record is deliberately flat — it exposes only the fields the client
 * needs. Most importantly, it surfaces the audit timestamp fields
 * ({@code createdAt} and {@code updatedAt}) that are the central feature of this
 * mini-project, so callers can verify that the values were set automatically by
 * the JPA Auditing infrastructure.
 *
 * <p>The static factory method {@link #from(Article)} keeps the mapping logic
 * co-located with the DTO class and avoids a separate mapper utility.
 *
 * @param id        database-generated primary key
 * @param title     article title
 * @param content   article body text
 * @param author    article author name
 * @param createdAt UTC instant when the entity was first persisted — set by {@code @CreatedDate}
 * @param updatedAt UTC instant of the most recent update — set by {@code @LastModifiedDate}
 */
public record ArticleResponse(
        Long id,
        String title,
        String content,
        String author,
        Instant createdAt,
        Instant updatedAt
) {

    /**
     * Creates an {@code ArticleResponse} from a persisted {@link Article} entity.
     *
     * <p>By the time this factory method is called, the JPA Auditing listener
     * will already have populated {@code createdAt} and {@code updatedAt} on the
     * entity, so they are guaranteed to be non-null.
     *
     * @param article the JPA entity to convert
     * @return a fully populated response DTO
     */
    public static ArticleResponse from(Article article) {
        return new ArticleResponse(
                article.getId(),
                article.getTitle(),
                article.getContent(),
                article.getAuthor(),
                article.getCreatedAt(),
                article.getUpdatedAt()
        );
    }
}
