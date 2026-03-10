package com.example.jpaauditing.service;

import com.example.jpaauditing.dto.ArticleRequest;
import com.example.jpaauditing.dto.ArticleResponse;
import com.example.jpaauditing.entity.Article;
import com.example.jpaauditing.exception.ArticleNotFoundException;
import com.example.jpaauditing.repository.ArticleRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Service layer for {@link Article} business logic.
 *
 * <p>This class is the only place in the application where {@link Article} entities
 * are created, mutated, and deleted. The JPA Auditing behaviour — automatic
 * population of {@code createdAt} and {@code updatedAt} — happens transparently
 * inside {@link ArticleRepository#save(Object)}: Spring Data JPA fires the
 * {@code @PrePersist} / {@code @PreUpdate} JPA lifecycle callbacks, which the
 * {@code AuditingEntityListener} intercepts to set the timestamp fields.
 *
 * <p>No timestamp-related code appears anywhere in this service — that is the
 * whole point of JPA Auditing.
 */
@Service
@Transactional(readOnly = true) // default: read-only transactions (no dirty checking overhead)
public class ArticleService {

    private final ArticleRepository articleRepository;

    /**
     * Constructor injection — the recommended injection style in Spring.
     * No {@code @Autowired} annotation needed on single-constructor classes.
     *
     * @param articleRepository repository for CRUD operations on {@link Article}
     */
    public ArticleService(ArticleRepository articleRepository) {
        this.articleRepository = articleRepository;
    }

    /**
     * Returns all articles in the database.
     *
     * @return list of all articles as response DTOs
     */
    public List<ArticleResponse> findAll() {
        return articleRepository.findAll()
                .stream()
                .map(ArticleResponse::from)
                .toList();
    }

    /**
     * Finds a single article by its primary key.
     *
     * @param id the article's database ID
     * @return the article as a response DTO
     * @throws ArticleNotFoundException if no article with that ID exists
     */
    public ArticleResponse findById(Long id) {
        Article article = articleRepository.findById(id)
                .orElseThrow(() -> new ArticleNotFoundException(id));
        return ArticleResponse.from(article);
    }

    /**
     * Finds all articles by a specific author.
     *
     * @param author the author's name
     * @return list of matching articles (may be empty)
     */
    public List<ArticleResponse> findByAuthor(String author) {
        return articleRepository.findByAuthor(author)
                .stream()
                .map(ArticleResponse::from)
                .toList();
    }

    /**
     * Creates a new article and persists it.
     *
     * <p>After {@code articleRepository.save(article)} returns, both
     * {@code article.getCreatedAt()} and {@code article.getUpdatedAt()} will be
     * non-null, having been set by the {@code AuditingEntityListener} during the
     * {@code @PrePersist} callback — before the INSERT is flushed to the database.
     *
     * @param request the inbound DTO with title, content and author
     * @return the persisted article (with auto-generated ID and audit timestamps)
     */
    @Transactional // overrides the class-level readOnly = true
    public ArticleResponse create(ArticleRequest request) {
        Article article = new Article(request.title(), request.content(), request.author());
        Article saved = articleRepository.save(article);
        return ArticleResponse.from(saved);
    }

    /**
     * Updates an existing article's title, content, and author.
     *
     * <p>After the save, {@code article.getUpdatedAt()} will contain a new timestamp
     * reflecting the moment of this update, while {@code article.getCreatedAt()}
     * remains unchanged (its column is declared {@code updatable = false}).
     *
     * @param id      the ID of the article to update
     * @param request the inbound DTO containing the new values
     * @return the updated article as a response DTO
     * @throws ArticleNotFoundException if no article with that ID exists
     */
    @Transactional
    public ArticleResponse update(Long id, ArticleRequest request) {
        Article article = articleRepository.findById(id)
                .orElseThrow(() -> new ArticleNotFoundException(id));

        // Mutate the managed entity — Hibernate detects the changes (dirty checking)
        // and will issue an UPDATE when the transaction commits.
        // The AuditingEntityListener's @PreUpdate callback fires at that point,
        // refreshing the updatedAt timestamp automatically.
        article.setTitle(request.title());
        article.setContent(request.content());
        article.setAuthor(request.author());

        Article saved = articleRepository.save(article);
        return ArticleResponse.from(saved);
    }

    /**
     * Deletes an article by its primary key.
     *
     * @param id the ID of the article to delete
     * @throws ArticleNotFoundException if no article with that ID exists
     */
    @Transactional
    public void delete(Long id) {
        if (!articleRepository.existsById(id)) {
            throw new ArticleNotFoundException(id);
        }
        articleRepository.deleteById(id);
    }
}
