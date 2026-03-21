package com.example.oauth2loginclient.integration;

import com.example.oauth2loginclient.domain.AppUser;
import com.example.oauth2loginclient.repository.AppUserRepository;
import com.example.oauth2loginclient.service.AppUserService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2UserAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.oauth2Login;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Full integration test for the OAuth2 Login Client.
 *
 * <h2>What this tests</h2>
 * <ul>
 *   <li>The full Spring MVC + Spring Security filter chain</li>
 *   <li>JPA persistence of {@code AppUser} entities to a real PostgreSQL instance
 *       managed by Testcontainers</li>
 *   <li>Access control: public endpoints, authenticated endpoints,
 *       and unauthenticated redirects</li>
 *   <li>The {@code /api/me} and {@code /api/users} endpoints with a simulated
 *       OAuth2 principal injected via
 *       {@link org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors#oauth2Login()}</li>
 * </ul>
 *
 * <h2>Why we use oauth2Login() post-processor</h2>
 * <p>The OAuth2 Authorization Code flow requires browser redirects to external
 * providers (GitHub/Google), which is impossible to replicate in an automated
 * test. Spring Security Test's {@code oauth2Login()} post-processor injects a
 * fully-authenticated {@link org.springframework.security.oauth2.client.authentication.OAuth2LoginAuthenticationToken}
 * directly into the {@code SecurityContext}, bypassing the redirect flow entirely.
 * This lets us test the downstream behaviour (persistence, response shape, access
 * control) without any real OAuth2 interaction.</p>
 *
 * <h2>Testcontainers</h2>
 * <p>{@link PostgreSQLContainer} is declared as a static field so that a single
 * container instance is shared across all test methods in this class. The
 * {@code @DynamicPropertySource} method wires the container's JDBC URL into the
 * Spring datasource configuration at runtime.</p>
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Testcontainers
class UserControllerIntegrationTest {

    // ── Testcontainers setup ─────────────────────────────────────────────────

    /**
     * A shared PostgreSQL container started once for the entire test class.
     *
     * <p>Declaring it {@code static} is important: Testcontainers starts the
     * container before the Spring context is created (because
     * {@code @DynamicPropertySource} must supply the JDBC URL before the
     * datasource bean is initialized).</p>
     */
    @Container
    @SuppressWarnings("resource")
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine")
            .withDatabaseName("oauth2logintest")
            .withUsername("test")
            .withPassword("test");

    /**
     * Registers the container's dynamic JDBC URL into the Spring
     * {@link org.springframework.core.env.Environment} so that
     * {@code spring.datasource.url} points to the Testcontainers-managed port.
     */
    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    // ── Spring beans ─────────────────────────────────────────────────────────

    /** MockMvc lets us send HTTP requests to the full Spring MVC filter chain. */
    @Autowired
    private MockMvc mockMvc;

    /** Service under test – used to seed the database and verify state. */
    @Autowired
    private AppUserService appUserService;

    /** Repository used to clean up between tests. */
    @Autowired
    private AppUserRepository userRepository;

    // ── Test fixtures ─────────────────────────────────────────────────────────

    /**
     * A pre-built OAuth2 principal that simulates a GitHub-authenticated user.
     * This is what {@code CustomOAuth2UserService#loadUser} would produce after
     * a real GitHub login.
     */
    private DefaultOAuth2User githubPrincipal;

    /**
     * A pre-built OAuth2 principal that simulates a Google-authenticated user.
     */
    private DefaultOAuth2User googlePrincipal;

    @BeforeEach
    void setUp() {
        // Build a simulated GitHub OAuth2User attribute map.
        // These are the fields GitHub returns in its UserInfo response.
        Map<String, Object> githubAttributes = Map.of(
                "id",         111111,           // GitHub returns integer user id
                "login",      "octocat",
                "name",       "The Octocat",
                "email",      "octocat@github.com",
                "avatar_url", "https://avatars.githubusercontent.com/u/583231"
        );

        // Build the DefaultOAuth2User using the "login" attribute as the name
        // key (GitHub uses "login" as the principal name).
        githubPrincipal = new DefaultOAuth2User(
                Set.of(new OAuth2UserAuthority(githubAttributes)),
                githubAttributes,
                "login"  // attribute key used as the principal name
        );

        // Build a simulated Google OAuth2User attribute map.
        // Google OIDC returns "sub" as the subject identifier and "name" as display name.
        Map<String, Object> googleAttributes = Map.of(
                "sub",     "google-sub-999",
                "name",    "Jane Doe",
                "email",   "jane.doe@gmail.com",
                "picture", "https://lh3.googleusercontent.com/photo/jane"
        );

        googlePrincipal = new DefaultOAuth2User(
                Set.of(new OAuth2UserAuthority(googleAttributes)),
                googleAttributes,
                "sub"  // Google uses "sub" as the name attribute key
        );

        // Seed the database with a persisted AppUser for the GitHub user so
        // that /api/me can find it when we query with the githubPrincipal.
        appUserService.upsertUser(
                "github", "111111",
                "The Octocat", "octocat@github.com",
                "https://avatars.githubusercontent.com/u/583231"
        );
    }

    @AfterEach
    void tearDown() {
        // Clean the database between tests to avoid cross-test pollution.
        // create-drop DDL handles the schema but not the data within a run.
        userRepository.deleteAll();
    }

    // ── Public endpoint tests ────────────────────────────────────────────────

    @Test
    @DisplayName("GET / → 200 OK with welcome message (no auth required)")
    void home_shouldReturn200_withoutAuthentication() throws Exception {
        mockMvc.perform(get("/").accept(MediaType.APPLICATION_JSON))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.message").value("Welcome to the OAuth2 Login Client"))
               .andExpect(jsonPath("$.loginLinks.github").value("/oauth2/authorization/github"))
               .andExpect(jsonPath("$.loginLinks.google").value("/oauth2/authorization/google"));
    }

    // ── Access control tests ─────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/me → 302 redirect to login when unauthenticated")
    void getMe_shouldRedirectToLogin_whenUnauthenticated() throws Exception {
        // Spring Security redirects unauthenticated requests to /login by default
        mockMvc.perform(get("/api/me").accept(MediaType.APPLICATION_JSON))
               .andExpect(status().is3xxRedirection());
    }

    @Test
    @DisplayName("GET /api/users → 302 redirect to login when unauthenticated")
    void getUsers_shouldRedirectToLogin_whenUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/users").accept(MediaType.APPLICATION_JSON))
               .andExpect(status().is3xxRedirection());
    }

    // ── /api/me tests ────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/me → 200 with GitHub user profile when authenticated as GitHub user")
    void getMe_shouldReturnGithubProfile_whenAuthenticatedAsGithubUser() throws Exception {
        // The oauth2Login() post-processor injects the githubPrincipal directly
        // into the SecurityContext, simulating a completed GitHub OAuth2 flow.
        mockMvc.perform(get("/api/me")
                       .with(oauth2Login().oauth2User(githubPrincipal))
                       .accept(MediaType.APPLICATION_JSON))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.provider").value("github"))
               .andExpect(jsonPath("$.name").value("The Octocat"))
               .andExpect(jsonPath("$.email").value("octocat@github.com"));
    }

    @Test
    @DisplayName("GET /api/me → 200 and user is persisted on first Google login")
    void getMe_shouldPersistGoogleUser_onFirstLogin() throws Exception {
        // First, upsert the Google user so /api/me can find them in the DB
        appUserService.upsertUser(
                "google", "google-sub-999",
                "Jane Doe", "jane.doe@gmail.com",
                "https://lh3.googleusercontent.com/photo/jane"
        );

        mockMvc.perform(get("/api/me")
                       .with(oauth2Login().oauth2User(googlePrincipal))
                       .accept(MediaType.APPLICATION_JSON))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.provider").value("google"))
               .andExpect(jsonPath("$.name").value("Jane Doe"))
               .andExpect(jsonPath("$.email").value("jane.doe@gmail.com"));
    }

    // ── /api/users tests ─────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/users → 200 with list containing all persisted users")
    void getAllUsers_shouldReturnAllUsers_whenAuthenticated() throws Exception {
        // Seed a second user (Google) in addition to the GitHub user from setUp()
        appUserService.upsertUser(
                "google", "google-sub-999",
                "Jane Doe", "jane.doe@gmail.com", null
        );

        mockMvc.perform(get("/api/users")
                       .with(oauth2Login().oauth2User(githubPrincipal))
                       .accept(MediaType.APPLICATION_JSON))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$", hasSize(2)))
               .andExpect(jsonPath("$[*].provider", containsInAnyOrder("github", "google")));
    }

    @Test
    @DisplayName("GET /api/users → 200 with empty list when no users have logged in")
    void getAllUsers_shouldReturnEmptyList_whenNoUsersExist() throws Exception {
        // Remove the user inserted by setUp()
        userRepository.deleteAll();

        mockMvc.perform(get("/api/users")
                       .with(oauth2Login().oauth2User(githubPrincipal))
                       .accept(MediaType.APPLICATION_JSON))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$", hasSize(0)));
    }

    // ── /api/users/{id} tests ────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/users/{id} → 200 with user profile for valid id")
    void getUserById_shouldReturnUser_whenIdExists() throws Exception {
        // Retrieve the persisted GitHub user to get its generated id
        AppUser saved = userRepository.findAll().get(0);

        mockMvc.perform(get("/api/users/{id}", saved.getId())
                       .with(oauth2Login().oauth2User(githubPrincipal))
                       .accept(MediaType.APPLICATION_JSON))
               .andExpect(status().isOk())
               .andExpect(jsonPath("$.id").value(saved.getId()))
               .andExpect(jsonPath("$.provider").value("github"));
    }

    @Test
    @DisplayName("GET /api/users/{id} → 404 when id does not exist")
    void getUserById_shouldReturn404_whenIdNotFound() throws Exception {
        mockMvc.perform(get("/api/users/999999")
                       .with(oauth2Login().oauth2User(githubPrincipal))
                       .accept(MediaType.APPLICATION_JSON))
               .andExpect(status().isNotFound());
    }

    // ── /api/me/attributes tests ─────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/me/attributes → 200 with raw OAuth2 attributes map")
    void getCurrentUserAttributes_shouldReturnAttributes_whenAuthenticated() throws Exception {
        mockMvc.perform(get("/api/me/attributes")
                       .with(oauth2Login().oauth2User(githubPrincipal))
                       .accept(MediaType.APPLICATION_JSON))
               .andExpect(status().isOk())
               // GitHub attributes should include "login" and "avatar_url"
               .andExpect(jsonPath("$.login").value("octocat"))
               .andExpect(jsonPath("$.avatar_url").isNotEmpty());
    }

    // ── Database persistence verification ───────────────────────────────────

    @Test
    @DisplayName("upsertUser: idempotent – second login updates existing record rather than creating duplicate")
    void upsertUser_shouldBeIdempotent_onSubsequentLogins() {
        // First login is done in setUp(); simulate a second login with updated name
        appUserService.upsertUser(
                "github", "111111",
                "Octocat Updated", "octocat@github.com",
                "https://avatars.githubusercontent.com/u/583231"
        );

        // Only one record should exist (upsert, not insert)
        List<AppUser> allUsers = userRepository.findAll();
        assertThat(allUsers).hasSize(1);
        assertThat(allUsers.get(0).getName()).isEqualTo("Octocat Updated");
    }
}
