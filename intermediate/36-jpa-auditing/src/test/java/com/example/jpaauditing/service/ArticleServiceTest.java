package com.example.jpaauditing.service;

import com.example.jpaauditing.dto.ArticleRequest;
import com.example.jpaauditing.dto.ArticleResponse;
import com.example.jpaauditing.entity.Article;
import com.example.jpaauditing.exception.ArticleNotFoundException;
import com.example.jpaauditing.repository.ArticleRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

/**
 * Unit tests for {@link ArticleService}.
 *
 * <p><b>Testing approach:</b>
 * <ul>
 *   <li>Uses Mockito to mock the {@link ArticleRepository} dependency so no
 *       database or Spring context is needed — tests run fast and in isolation.</li>
 *   <li>{@code @ExtendWith(MockitoExtension.class)} integrates Mockito's lifecycle
 *       with JUnit 5, automatically initialising mocks and verifying them after
 *       each test without needing {@code MockitoAnnotations.openMocks(this)}.</li>
 *   <li>Uses AssertJ for fluent, readable assertions.</li>
 *   <li>BDD-style {@code given / when / then} pattern keeps tests easy to follow.</li>
 * </ul>
 *
 * <p><b>What these tests cover:</b>
 * <ul>
 *   <li>Happy-path CRUD operations (findAll, findById, create, update, delete).</li>
 *   <li>Error paths — {@link ArticleNotFoundException} is thrown when the requested
 *       article does not exist.</li>
 *   <li>The mapping logic from entity to DTO (including audit timestamp fields).</li>
 * </ul>
 *
 * <p><b>Note:</b> The JPA Auditing mechanism itself (automatic timestamp population
 * via {@code AuditingEntityListener}) is not exercised in these unit tests because
 * there is no JPA context. That behaviour is verified in the integration tests
 * ({@code ArticleRepositoryIntegrationTest}) which use a real database via
 * Testcontainers.
 */
@ExtendWith(MockitoExtension.class)
class ArticleServiceTest {

    @Mock
    private ArticleRepository articleRepository;

    @InjectMocks
    private ArticleService articleService;

    // Shared test fixtures — rebuilt before each test to guarantee isolation
    private Article sampleArticle;
    private Instant sampleCreatedAt;
    private Instant sampleUpdatedAt;

    /**
     * Sets up a fully populated {@link Article} fixture that simulates what the
     * repository would return after a successful INSERT (i.e. the entity already
     * has an ID and audit timestamps populated by the JPA Auditing listener).
     *
     * <p>We use reflection to set the {@code id} field because the Article entity
     * uses a protected no-arg constructor and does not expose a setter for {@code id}.
     * In unit tests it is acceptable to fake the repository return value this way.
     */
    @BeforeEach
    void setUp() throws Exception {
        sampleCreatedAt = Instant.parse("2024-01-01T10:00:00Z");
        sampleUpdatedAt = Instant.parse("2024-01-02T12:00:00Z");

        sampleArticle = new Article("Spring JPA", "JPA content", "Alice");

        // Inject ID via reflection (no public setter — ID is DB-generated)
        var idField = Article.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(sampleArticle, 1L);

        // Inject createdAt via reflection on the superclass Auditable
        var createdAtField = sampleArticle.getClass().getSuperclass().getDeclaredField("createdAt");
        createdAtField.setAccessible(true);
        createdAtField.set(sampleArticle, sampleCreatedAt);

        // Inject updatedAt via reflection on the superclass Auditable
        var updatedAtField = sampleArticle.getClass().getSuperclass().getDeclaredField("updatedAt");
        updatedAtField.setAccessible(true);
        updatedAtField.set(sampleArticle, sampleUpdatedAt);
    }

    // =========================================================================
    // findAll
    // =========================================================================

    @Test
    @DisplayName("findAll returns list of ArticleResponse DTOs mapped from all entities")
    void findAll_returnsAllArticlesAsDtos() {
        // given — repository returns a single article
        given(articleRepository.findAll()).willReturn(List.of(sampleArticle));

        // when
        List<ArticleResponse> result = articleService.findAll();

        // then
        assertThat(result).hasSize(1);
        ArticleResponse response = result.get(0);
        assertThat(response.id()).isEqualTo(1L);
        assertThat(response.title()).isEqualTo("Spring JPA");
        assertThat(response.content()).isEqualTo("JPA content");
        assertThat(response.author()).isEqualTo("Alice");
        // Audit timestamps must be forwarded from the entity to the DTO
        assertThat(response.createdAt()).isEqualTo(sampleCreatedAt);
        assertThat(response.updatedAt()).isEqualTo(sampleUpdatedAt);
    }

    @Test
    @DisplayName("findAll returns empty list when no articles exist")
    void findAll_returnsEmptyList_whenNoArticles() {
        // given
        given(articleRepository.findAll()).willReturn(List.of());

        // when
        List<ArticleResponse> result = articleService.findAll();

        // then
        assertThat(result).isEmpty();
    }

    // =========================================================================
    // findById
    // =========================================================================

    @Test
    @DisplayName("findById returns the correct ArticleResponse DTO when article exists")
    void findById_returnsDto_whenArticleExists() {
        // given
        given(articleRepository.findById(1L)).willReturn(Optional.of(sampleArticle));

        // when
        ArticleResponse result = articleService.findById(1L);

        // then
        assertThat(result.id()).isEqualTo(1L);
        assertThat(result.title()).isEqualTo("Spring JPA");
        assertThat(result.createdAt()).isEqualTo(sampleCreatedAt);
        assertThat(result.updatedAt()).isEqualTo(sampleUpdatedAt);
    }

    @Test
    @DisplayName("findById throws ArticleNotFoundException when article does not exist")
    void findById_throwsNotFoundException_whenArticleNotFound() {
        // given
        given(articleRepository.findById(99L)).willReturn(Optional.empty());

        // when / then
        assertThatThrownBy(() -> articleService.findById(99L))
                .isInstanceOf(ArticleNotFoundException.class)
                .hasMessageContaining("99");
    }

    // =========================================================================
    // findByAuthor
    // =========================================================================

    @Test
    @DisplayName("findByAuthor returns articles filtered by author name")
    void findByAuthor_returnsFilteredArticles() {
        // given
        given(articleRepository.findByAuthor("Alice")).willReturn(List.of(sampleArticle));

        // when
        List<ArticleResponse> result = articleService.findByAuthor("Alice");

        // then
        assertThat(result).hasSize(1);
        assertThat(result.get(0).author()).isEqualTo("Alice");
    }

    // =========================================================================
    // create
    // =========================================================================

    @Test
    @DisplayName("create saves a new Article and returns the response DTO with audit timestamps")
    void create_savesArticleAndReturnsDtoWithAuditTimestamps() {
        // given — the request DTO sent by the client
        ArticleRequest request = new ArticleRequest("New Title", "New Content", "Bob");

        // Simulate what the repository + JPA Auditing listener returns after the INSERT:
        // a fully persisted entity with ID and timestamps already set.
        given(articleRepository.save(any(Article.class))).willReturn(sampleArticle);

        // when
        ArticleResponse result = articleService.create(request);

        // then — the service must have called save exactly once
        then(articleRepository).should().save(any(Article.class));

        // The returned DTO must carry the audit timestamps set by the auditing listener
        assertThat(result.createdAt()).isNotNull();
        assertThat(result.updatedAt()).isNotNull();
        assertThat(result.id()).isEqualTo(1L);
    }

    // =========================================================================
    // update
    // =========================================================================

    @Test
    @DisplayName("update modifies the article fields and returns the updated DTO")
    void update_modifiesArticleAndReturnsUpdatedDto() throws Exception {
        // given — prepare an updated-state article to be returned by save()
        Article updatedArticle = new Article("Updated Title", "Updated Content", "Alice");
        var idField = Article.class.getDeclaredField("id");
        idField.setAccessible(true);
        idField.set(updatedArticle, 1L);

        Instant updatedInstant = Instant.parse("2024-06-01T09:00:00Z");
        var createdAtField = updatedArticle.getClass().getSuperclass().getDeclaredField("createdAt");
        createdAtField.setAccessible(true);
        createdAtField.set(updatedArticle, sampleCreatedAt); // createdAt must NOT change

        var updatedAtField = updatedArticle.getClass().getSuperclass().getDeclaredField("updatedAt");
        updatedAtField.setAccessible(true);
        updatedAtField.set(updatedArticle, updatedInstant); // updatedAt must be refreshed

        given(articleRepository.findById(1L)).willReturn(Optional.of(sampleArticle));
        given(articleRepository.save(any(Article.class))).willReturn(updatedArticle);

        ArticleRequest updateRequest = new ArticleRequest("Updated Title", "Updated Content", "Alice");

        // when
        ArticleResponse result = articleService.update(1L, updateRequest);

        // then
        assertThat(result.title()).isEqualTo("Updated Title");
        // createdAt is preserved; updatedAt is refreshed
        assertThat(result.createdAt()).isEqualTo(sampleCreatedAt);
        assertThat(result.updatedAt()).isEqualTo(updatedInstant);
        assertThat(result.updatedAt()).isAfter(result.createdAt());
    }

    @Test
    @DisplayName("update throws ArticleNotFoundException when article does not exist")
    void update_throwsNotFoundException_whenArticleNotFound() {
        // given
        given(articleRepository.findById(99L)).willReturn(Optional.empty());
        ArticleRequest request = new ArticleRequest("Title", "Content", "Author");

        // when / then
        assertThatThrownBy(() -> articleService.update(99L, request))
                .isInstanceOf(ArticleNotFoundException.class)
                .hasMessageContaining("99");

        // The repository save must never be called when the entity is not found
        then(articleRepository).should(never()).save(any());
    }

    // =========================================================================
    // delete
    // =========================================================================

    @Test
    @DisplayName("delete removes the article when it exists")
    void delete_deletesArticle_whenExists() {
        // given
        given(articleRepository.existsById(1L)).willReturn(true);

        // when
        articleService.delete(1L);

        // then — deleteById must be called exactly once with the correct ID
        then(articleRepository).should().deleteById(1L);
    }

    @Test
    @DisplayName("delete throws ArticleNotFoundException when article does not exist")
    void delete_throwsNotFoundException_whenArticleNotFound() {
        // given
        given(articleRepository.existsById(99L)).willReturn(false);

        // when / then
        assertThatThrownBy(() -> articleService.delete(99L))
                .isInstanceOf(ArticleNotFoundException.class)
                .hasMessageContaining("99");

        // deleteById must never be called when the entity does not exist
        then(articleRepository).should(never()).deleteById(any());
    }
}
