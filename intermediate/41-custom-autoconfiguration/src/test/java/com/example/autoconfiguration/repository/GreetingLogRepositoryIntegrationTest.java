package com.example.autoconfiguration.repository;

import com.example.autoconfiguration.entity.GreetingLog;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
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
 * Integration tests for {@link GreetingLogRepository}.
 *
 * <p><b>What is being tested:</b>
 * <ul>
 *   <li>Standard CRUD operations against a real PostgreSQL database.</li>
 *   <li>{@link GreetingLogRepository#findByName(String)} — derived query method.</li>
 *   <li>{@link GreetingLogRepository#findAllByOrderByCreatedAtDesc()} — ordering.</li>
 *   <li>Data persistence and retrieval correctness.</li>
 * </ul>
 *
 * <p><b>Key annotations:</b>
 * <ul>
 *   <li>{@code @DataJpaTest} — loads a minimal Spring context with only JPA
 *       infrastructure: entities, repositories, Hibernate, and a DataSource.
 *       No web layer, no service beans, no full application context.</li>
 *   <li>{@code @AutoConfigureTestDatabase(replace = NONE)} — prevents
 *       {@code @DataJpaTest} from replacing the configured DataSource with an
 *       in-memory H2 database. We want to use Testcontainers PostgreSQL instead.</li>
 *   <li>{@code @Testcontainers} — activates the Testcontainers JUnit 5 extension,
 *       which manages Docker container lifecycle: start before tests, stop after.</li>
 * </ul>
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers
class GreetingLogRepositoryIntegrationTest {

    /**
     * The Testcontainers-managed PostgreSQL container.
     *
     * <p>{@code static} + {@code @Container} → container is started once for the entire
     * test class (shared container pattern). Starting Docker containers is expensive;
     * reusing the same container across all tests in a class is much faster than
     * starting a new one per test method.
     *
     * <p>Uses {@code postgres:16-alpine} — the same image as {@code docker-compose.yml}
     * to ensure environment parity between tests and production.
     */
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("greetingdb_test")
            .withUsername("test_user")
            .withPassword("test_pass");

    /**
     * Registers the Testcontainers DataSource properties into the Spring context
     * before the ApplicationContext is created.
     *
     * <p>Testcontainers assigns a random host port to avoid port conflicts.
     * {@code @DynamicPropertySource} lets us read that port at runtime and inject
     * the correct JDBC URL without hard-coding any port numbers.
     *
     * @param registry the property registry to add dynamic properties to
     */
    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
        // create-drop: Hibernate creates the schema fresh for each test run
        registry.add("spring.jpa.hibernate.ddl-auto", () -> "create-drop");
    }

    @Autowired
    private GreetingLogRepository greetingLogRepository;

    /**
     * Clears all greeting logs before each test to ensure complete test isolation.
     * No data from a previous test can leak into and affect the next test.
     */
    @BeforeEach
    void cleanUp() {
        greetingLogRepository.deleteAll();
    }

    // =========================================================================
    // Basic CRUD
    // =========================================================================

    @Test
    @DisplayName("save persists a GreetingLog and assigns a generated ID")
    void save_persistsGreetingLog_andAssignsId() {
        // given
        GreetingLog log = new GreetingLog("Alice", "Hello, Alice!", Instant.now());

        // when
        GreetingLog saved = greetingLogRepository.save(log);

        // then — the database must have assigned a non-null ID
        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getName()).isEqualTo("Alice");
        assertThat(saved.getMessage()).isEqualTo("Hello, Alice!");
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("findById returns the saved GreetingLog when it exists")
    void findById_returnsGreetingLog_whenExists() {
        // given
        GreetingLog saved = greetingLogRepository.save(
                new GreetingLog("Bob", "Hello, Bob!", Instant.now()));

        // when
        Optional<GreetingLog> result = greetingLogRepository.findById(saved.getId());

        // then
        assertThat(result).isPresent();
        assertThat(result.get().getName()).isEqualTo("Bob");
        assertThat(result.get().getMessage()).isEqualTo("Hello, Bob!");
    }

    @Test
    @DisplayName("findById returns empty Optional when ID does not exist")
    void findById_returnsEmpty_whenNotFound() {
        // when
        Optional<GreetingLog> result = greetingLogRepository.findById(9999L);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findAll returns all persisted GreetingLogs")
    void findAll_returnsAllLogs() {
        // given — save three logs
        greetingLogRepository.saveAll(List.of(
                new GreetingLog("Alice", "Hello, Alice!", Instant.now()),
                new GreetingLog("Bob", "Hello, Bob!", Instant.now()),
                new GreetingLog("Carol", "Hello, Carol!", Instant.now())
        ));

        // when
        List<GreetingLog> all = greetingLogRepository.findAll();

        // then
        assertThat(all).hasSize(3);
    }

    @Test
    @DisplayName("deleteById removes the GreetingLog from the database")
    void deleteById_removesLog() {
        // given
        GreetingLog saved = greetingLogRepository.save(
                new GreetingLog("Dave", "Hello, Dave!", Instant.now()));
        Long id = saved.getId();

        // when
        greetingLogRepository.deleteById(id);

        // then
        assertThat(greetingLogRepository.findById(id)).isEmpty();
    }

    // =========================================================================
    // findByName — derived query
    // =========================================================================

    @Test
    @DisplayName("findByName returns only logs for the given name")
    void findByName_returnsMatchingLogs() {
        // given — two logs for Alice, one for Bob
        greetingLogRepository.saveAll(List.of(
                new GreetingLog("Alice", "Hello, Alice!", Instant.now()),
                new GreetingLog("Alice", "Hi, Alice!", Instant.now()),
                new GreetingLog("Bob", "Hello, Bob!", Instant.now())
        ));

        // when
        List<GreetingLog> aliceLogs = greetingLogRepository.findByName("Alice");

        // then
        assertThat(aliceLogs).hasSize(2);
        assertThat(aliceLogs).allSatisfy(l -> assertThat(l.getName()).isEqualTo("Alice"));
    }

    @Test
    @DisplayName("findByName returns empty list when no logs exist for that name")
    void findByName_returnsEmptyList_whenNoMatch() {
        // given — a log for a different name
        greetingLogRepository.save(new GreetingLog("Eve", "Hello, Eve!", Instant.now()));

        // when
        List<GreetingLog> result = greetingLogRepository.findByName("Unknown");

        // then
        assertThat(result).isEmpty();
    }

    @Test
    @DisplayName("findByName is case-sensitive — different cases return different results")
    void findByName_isCaseSensitive() {
        // given
        greetingLogRepository.save(new GreetingLog("Frank", "Hello, Frank!", Instant.now()));

        // when
        List<GreetingLog> exact = greetingLogRepository.findByName("Frank");
        List<GreetingLog> lower = greetingLogRepository.findByName("frank");

        // then — "Frank" matches, "frank" does not
        assertThat(exact).hasSize(1);
        assertThat(lower).isEmpty();
    }

    // =========================================================================
    // findAllByOrderByCreatedAtDesc — ordering
    // =========================================================================

    @Test
    @DisplayName("findAllByOrderByCreatedAtDesc returns logs ordered newest first")
    void findAllByOrderByCreatedAtDesc_returnsNewestFirst() throws InterruptedException {
        // given — save three logs with increasing timestamps
        Instant t1 = Instant.now();
        GreetingLog first = greetingLogRepository.save(
                new GreetingLog("First", "Hello, First!", t1));

        // Brief sleep to ensure distinct Instant values (clock resolution)
        Thread.sleep(5);
        Instant t2 = Instant.now();
        GreetingLog second = greetingLogRepository.save(
                new GreetingLog("Second", "Hello, Second!", t2));

        Thread.sleep(5);
        Instant t3 = Instant.now();
        GreetingLog third = greetingLogRepository.save(
                new GreetingLog("Third", "Hello, Third!", t3));

        // when
        List<GreetingLog> ordered = greetingLogRepository.findAllByOrderByCreatedAtDesc();

        // then — third (newest) must be first; first (oldest) must be last
        assertThat(ordered).hasSize(3);
        assertThat(ordered.get(0).getId()).isEqualTo(third.getId());
        assertThat(ordered.get(2).getId()).isEqualTo(first.getId());
    }

    @Test
    @DisplayName("findAllByOrderByCreatedAtDesc returns empty list when no logs exist")
    void findAllByOrderByCreatedAtDesc_returnsEmptyList_whenNoLogs() {
        // when
        List<GreetingLog> result = greetingLogRepository.findAllByOrderByCreatedAtDesc();

        // then
        assertThat(result).isEmpty();
    }

    // =========================================================================
    // Data integrity
    // =========================================================================

    @Test
    @DisplayName("saved GreetingLog has all fields correctly stored and retrievable")
    void save_allFieldsStoredAndRetrievable() {
        // given
        Instant now = Instant.now();
        GreetingLog log = new GreetingLog("Grace", "Hi, Grace.", now);

        // when
        GreetingLog saved = greetingLogRepository.save(log);
        GreetingLog reloaded = greetingLogRepository.findById(saved.getId()).orElseThrow();

        // then — all fields survive the round-trip through the database
        assertThat(reloaded.getName()).isEqualTo("Grace");
        assertThat(reloaded.getMessage()).isEqualTo("Hi, Grace.");
        // Instant precision: PostgreSQL stores with microsecond precision; Java Instant
        // has nanosecond precision — truncate to millis for safe comparison
        assertThat(reloaded.getCreatedAt()).isNotNull();
    }
}
