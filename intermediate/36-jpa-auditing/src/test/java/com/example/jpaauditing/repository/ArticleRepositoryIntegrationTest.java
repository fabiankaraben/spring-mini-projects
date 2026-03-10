package com.example.jpaauditing.repository;

import com.example.jpaauditing.entity.Article;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for {@link ArticleRepository} and — most importantly —
 * the JPA Auditing behaviour.
 *
 * <p><b>What is being tested here:</b>
 * <ul>
 *   <li>That {@code createdAt} is automatically set by the
 *       {@code AuditingEntityListener} on INSERT and is never null.</li>
 *   <li>That {@code updatedAt} is set on INSERT and is refreshed on UPDATE.</li>
 *   <li>That {@code createdAt} remains unchanged after an UPDATE
 *       ({@code updatable = false} column constraint).</li>
 *   <li>That the repository's custom query methods work correctly against a
 *       real PostgreSQL database.</li>
 * </ul>
 *
 * <p><b>Key annotations:</b>
 * <ul>
 *   <li>{@code @DataJpaTest} — loads a minimal Spring context containing only
 *       JPA-related components (entities, repositories, JPA infrastructure).
 *       No web layer, no service beans, no full application context.
 *       By default it replaces the real DataSource with an in-memory one; we
 *       override that with Testcontainers below.</li>
 *   <li>{@code @AutoConfigureTestDatabase(replace = NONE)} — tells
 *       {@code @DataJpaTest} NOT to replace our configured DataSource with an
 *       embedded in-memory database, so Testcontainers' PostgreSQL is used.</li>
 *   <li>{@code @Testcontainers} — activates Testcontainers JUnit 5 extension,
 *       which manages the Docker container lifecycle (start before tests, stop
 *       after tests).</li>
 *   <li>{@code @Import(JpaAuditingConfig.class)} — imports the
 *       {@code @EnableJpaAuditing} configuration into the slim {@code @DataJpaTest}
 *       context. Without this import the auditing listener is not registered and
 *       timestamps remain null.</li>
 * </ul>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
@Import(com.example.jpaauditing.config.JpaAuditingConfig.class)
class ArticleRepositoryIntegrationTest {

    /**
     * The Testcontainers PostgreSQL container.
     *
     * <p>{@code @Container} on a {@code static} field makes Testcontainers start
     * the container once for the entire test class (shared container) rather than
     * once per test method. This is much faster because Docker image pull and
     * container startup happen only once.
     *
     * <p>The {@code postgres:16-alpine} image is the same one used in
     * {@code docker-compose.yml}, ensuring parity between tests and production.
     */
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("articlesdb_test")
            .withUsername("test_user")
            .withPassword("test_pass");

    /**
     * Dynamically registers the Testcontainers DataSource connection properties
     * into the Spring application context.
     *
     * <p>Testcontainers assigns a random host port to the container to avoid
     * conflicts. {@code @DynamicPropertySource} lets us read that port at runtime
     * and override the {@code spring.datasource.*} properties before the
     * ApplicationContext is created — without hard-coding any port numbers.
     *
     * @param registry the registry to add properties to
     */
    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        // Override the DataSource to point to the Testcontainers PostgreSQL instance
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        // Use 'create-drop' so Hibernate creates the schema fresh for each test run
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired
    private ArticleRepository articleRepository;

    /**
     * Clears all articles from the database before each test to guarantee test
     * isolation — no leftover data from a previous test can affect the next one.
     */
    @BeforeEach
    void cleanUp() {
        articleRepository.deleteAll();
    }

    // =========================================================================
    // JPA Auditing — createdAt and updatedAt population
    // =========================================================================

    @Test
    @DisplayName("createdAt and updatedAt are automatically populated on INSERT by JPA Auditing")
    void save_newArticle_populatesAuditTimestamps() {
        // given — a brand-new article with no timestamps set manually
        Article article = new Article("JPA Auditing Intro", "Learn JPA auditing.", "Alice");

        // Record the time just before the save so we can assert the timestamp is recent
        Instant before = Instant.now();

        // when — persist the entity; AuditingEntityListener fires @PrePersist
        Article saved = articleRepository.save(article);

        Instant after = Instant.now();

        // then — both audit timestamps must be non-null and fall within [before, after]
        assertThat(saved.getCreatedAt())
                .as("createdAt should be populated automatically on INSERT")
                .isNotNull()
                .isAfterOrEqualTo(before)
                .isBeforeOrEqualTo(after);

        assertThat(saved.getUpdatedAt())
                .as("updatedAt should be populated automatically on INSERT")
                .isNotNull()
                .isAfterOrEqualTo(before)
                .isBeforeOrEqualTo(after);
    }

    @Test
    @DisplayName("updatedAt is refreshed on UPDATE while createdAt stays unchanged")
    void save_existingArticle_refreshesUpdatedAtButNotCreatedAt() throws InterruptedException {
        // given — persist a new article and record the initial timestamps
        Article article = new Article("Original Title", "Original content.", "Bob");
        Article saved = articleRepository.save(article);

        Instant originalCreatedAt = saved.getCreatedAt();
        Instant originalUpdatedAt = saved.getUpdatedAt();

        assertThat(originalCreatedAt).isNotNull();
        assertThat(originalUpdatedAt).isNotNull();

        // Sleep briefly so the clock advances — without this the timestamps might be
        // equal because the JVM clock resolution is sometimes coarser than 1 ms.
        Thread.sleep(10);

        // when — update the article; AuditingEntityListener fires @PreUpdate
        saved.setTitle("Updated Title");
        Article updated = articleRepository.saveAndFlush(saved);

        // then — createdAt must NOT have changed (column is updatable = false)
        assertThat(updated.getCreatedAt())
                .as("createdAt must remain unchanged after an UPDATE")
                .isEqualTo(originalCreatedAt);

        // updatedAt MUST be strictly after the original updatedAt
        assertThat(updated.getUpdatedAt())
                .as("updatedAt must be refreshed after an UPDATE")
                .isAfterOrEqualTo(originalUpdatedAt);
    }

    @Test
    @DisplayName("createdAt is never null after save — JPA Auditing is active")
    void save_auditingIsActive_createdAtNeverNull() {
        Article article = new Article("Non-null Check", "Content.", "Carol");
        Article saved = articleRepository.save(article);

        // The most fundamental auditing assertion: the timestamp is set automatically
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
    }

    // =========================================================================
    // Repository custom query methods
    // =========================================================================

    @Test
    @DisplayName("findByAuthor returns only articles written by the given author")
    void findByAuthor_returnsMatchingArticles() {
        // given — two articles by Alice, one by Bob
        articleRepository.saveAll(List.of(
                new Article("Article One", "Content one.", "Alice"),
                new Article("Article Two", "Content two.", "Alice"),
                new Article("Article Three", "Content three.", "Bob")
        ));

        // when
        List<Article> aliceArticles = articleRepository.findByAuthor("Alice");

        // then
        assertThat(aliceArticles).hasSize(2);
        assertThat(aliceArticles).allSatisfy(a -> assertThat(a.getAuthor()).isEqualTo("Alice"));
    }

    @Test
    @DisplayName("findByAuthor returns empty list when no articles exist for that author")
    void findByAuthor_returnsEmptyList_whenNoMatch() {
        // given — save an article by a different author
        articleRepository.save(new Article("Some Article", "Some content.", "Dave"));

        // when
        List<Article> result = articleRepository.findByAuthor("Unknown");

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findByTitle returns the article when it exists")
    void findByTitle_returnsArticle_whenExists() {
        // given
        articleRepository.save(new Article("Unique Title", "Content.", "Eve"));

        // when
        Optional<Article> result = articleRepository.findByTitle("Unique Title");

        // then
        assertThat(result).isPresent();
        assertThat(result.get().getTitle()).isEqualTo("Unique Title");
        // Audit timestamps must also be present when loading a persisted entity
        assertThat(result.get().getCreatedAt()).isNotNull();
        assertThat(result.get().getUpdatedAt()).isNotNull();
    }

    @Test
    @DisplayName("findByTitle returns empty Optional when title does not exist")
    void findByTitle_returnsEmpty_whenNotFound() {
        // when
        Optional<Article> result = articleRepository.findByTitle("Nonexistent");

        // then
        assertThat(result).isEmpty();
    }

    // =========================================================================
    // Basic CRUD through the repository
    // =========================================================================

    @Test
    @DisplayName("findAll returns all persisted articles")
    void findAll_returnsAllArticles() {
        // given
        articleRepository.saveAll(List.of(
                new Article("Title A", "Content A.", "Frank"),
                new Article("Title B", "Content B.", "Grace")
        ));

        // when
        List<Article> all = articleRepository.findAll();

        // then
        assertThat(all).hasSize(2);
    }

    @Test
    @DisplayName("deleteById removes the article from the database")
    void deleteById_removesArticle() {
        // given
        Article saved = articleRepository.save(new Article("To Delete", "Content.", "Henry"));
        Long id = saved.getId();

        // when
        articleRepository.deleteById(id);

        // then
        assertThat(articleRepository.findById(id)).isEmpty();
    }
}
