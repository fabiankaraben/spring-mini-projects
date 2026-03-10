package com.example.elasticsearchcrud.exception;

/**
 * Thrown by the service layer when an article with the requested ID does not
 * exist in the Elasticsearch index.
 *
 * <p>This is a custom unchecked exception (extends {@link RuntimeException}) so
 * callers do not need to declare it in their {@code throws} clause. The
 * {@link GlobalExceptionHandler} maps it to an HTTP 404 response.
 */
public class ArticleNotFoundException extends RuntimeException {

    /**
     * @param id the document ID that was not found in the index
     */
    public ArticleNotFoundException(String id) {
        super("Article not found with id: " + id);
    }
}
