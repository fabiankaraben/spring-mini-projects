package com.example.dockercomposesupport.dto;

import com.example.dockercomposesupport.domain.Book;

import java.time.LocalDateTime;

/**
 * Outgoing response payload representing a book.
 *
 * <p>Rather than returning the JPA entity directly from the controller
 * (which would expose internal Hibernate proxies and coupling to the
 * persistence layer), we map it to this immutable record.
 * This gives full control over which fields are visible in the API.</p>
 *
 * @param id              auto-generated primary key
 * @param title           book title
 * @param author          author name
 * @param isbn            ISBN identifier
 * @param publicationYear year of publication (nullable)
 * @param description     optional description or summary
 * @param createdAt       timestamp of record creation
 * @param updatedAt       timestamp of last update
 */
public record BookResponse(
        Long id,
        String title,
        String author,
        String isbn,
        Integer publicationYear,
        String description,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    /**
     * Factory method to convert a {@link Book} entity into a {@link BookResponse}.
     *
     * <p>Using a static factory keeps the mapping logic co-located with the DTO
     * and avoids a separate mapper class for this simple case.</p>
     *
     * @param book the entity to convert
     * @return the corresponding response DTO
     */
    public static BookResponse from(Book book) {
        return new BookResponse(
                book.getId(),
                book.getTitle(),
                book.getAuthor(),
                book.getIsbn(),
                book.getPublicationYear(),
                book.getDescription(),
                book.getCreatedAt(),
                book.getUpdatedAt()
        );
    }
}
