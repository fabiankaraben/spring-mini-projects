package com.example.formlogin.controller;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.user;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Full integration tests for the form login and logout flow.
 *
 * <p><strong>What is tested here:</strong>
 * <ul>
 *   <li>Public access to the login page</li>
 *   <li>Redirect of unauthenticated requests to /login</li>
 *   <li>Successful form login → session established → redirect to /dashboard</li>
 *   <li>Failed login → redirect to /login?error</li>
 *   <li>Role-based access: regular user cannot access /admin (403)</li>
 *   <li>Admin user can access /admin</li>
 *   <li>Logout → session invalidated → redirect to /login?logout</li>
 * </ul>
 *
 * <p><strong>Why Testcontainers?</strong>
 * These tests start a real PostgreSQL container via Testcontainers so that the
 * full Spring context (including JPA, {@link com.example.formlogin.config.DataInitializer},
 * and Spring Security) is exercised with a real database. This catches issues
 * that mocked databases cannot, such as SQL dialect problems or migration errors.
 *
 * <p><strong>@SpringBootTest</strong> loads the complete application context.
 * <strong>@AutoConfigureMockMvc</strong> injects a {@link MockMvc} instance that
 * drives the HTTP layer without starting a real TCP server, which is faster and
 * simpler for testing.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class PageControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    /**
     * A single PostgreSQL container shared across all test methods in this class.
     * Testcontainers starts it once before the first test and stops it after the last.
     * The {@code @Container} annotation on a static field triggers this lifecycle.
     */
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");

    /**
     * Overrides the DataSource properties with the dynamically assigned host/port
     * provided by the Testcontainers container. This is how we connect the Spring
     * context to the ephemeral container instead of the configured Docker Compose URL.
     *
     * @param registry the dynamic property registry provided by Spring Test
     */
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @BeforeAll
    static void beforeAll() {
        // Ensure the container is running before the Spring context starts
        postgres.start();
    }

    @AfterAll
    static void afterAll() {
        // Release the container resources after all tests in this class have run
        postgres.stop();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Login page
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /login → accessible without authentication (HTTP 200)")
    void loginPage_IsPubliclyAccessible() throws Exception {
        // The login page must be accessible to unauthenticated users; otherwise
        // Spring Security would redirect them to /login → infinite redirect loop.
        mockMvc.perform(get("/login"))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.TEXT_HTML))
                .andExpect(content().string(containsString("Sign in")));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Unauthenticated access to protected pages
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /dashboard without auth → redirected to /login")
    void dashboard_WithoutAuth_RedirectsToLogin() throws Exception {
        // Spring Security should intercept unauthenticated requests and send a
        // 302 redirect to the configured login page.
        mockMvc.perform(get("/dashboard"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    @DisplayName("GET /admin without auth → redirected to /login")
    void adminPage_WithoutAuth_RedirectsToLogin() throws Exception {
        mockMvc.perform(get("/admin"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    @Test
    @DisplayName("GET /profile without auth → redirected to /login")
    void profilePage_WithoutAuth_RedirectsToLogin() throws Exception {
        mockMvc.perform(get("/profile"))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Form login – success
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /login with valid user credentials → redirect to /dashboard")
    void formLogin_ValidUserCredentials_RedirectsToDashboard() throws Exception {
        // Simulate the browser submitting the login form.
        // csrf() injects a valid CSRF token so the request passes CSRF validation.
        mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "user")
                        .param("password", "password")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard"));
    }

    @Test
    @DisplayName("POST /login with valid admin credentials → redirect to /dashboard")
    void formLogin_ValidAdminCredentials_RedirectsToDashboard() throws Exception {
        mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "admin")
                        .param("password", "admin123")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/dashboard"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Form login – failure
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /login with wrong password → redirect to /login?error")
    void formLogin_WrongPassword_RedirectsToLoginWithError() throws Exception {
        mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "user")
                        .param("password", "wrongpassword")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error"));
    }

    @Test
    @DisplayName("POST /login with non-existent user → redirect to /login?error")
    void formLogin_NonExistentUser_RedirectsToLoginWithError() throws Exception {
        mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "nobody")
                        .param("password", "anything")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?error"));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Authenticated access with injected security context (MockMvc shortcut)
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /dashboard as authenticated user → HTTP 200 with username in body")
    void dashboard_AsAuthenticatedUser_ReturnsOk() throws Exception {
        // user(...) injects a pre-authenticated principal into the MockMvc request,
        // bypassing the login form – useful to test protected pages directly.
        mockMvc.perform(get("/dashboard")
                        .with(user("user").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("user")));
    }

    @Test
    @DisplayName("GET /profile as authenticated user → HTTP 200 with username in body")
    void profilePage_AsAuthenticatedUser_ReturnsOk() throws Exception {
        mockMvc.perform(get("/profile")
                        .with(user("user").roles("USER")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("user")));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Role-based access control
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("GET /admin as regular user → HTTP 403 Forbidden")
    void adminPage_AsRegularUser_ReturnsForbidden() throws Exception {
        // A user with only ROLE_USER should be denied access to /admin.
        // Spring Security returns 403 (not 401) when the user IS authenticated
        // but lacks the required role.
        mockMvc.perform(get("/admin")
                        .with(user("user").roles("USER")))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("GET /admin as admin user → HTTP 200 with admin content")
    void adminPage_AsAdminUser_ReturnsOk() throws Exception {
        mockMvc.perform(get("/admin")
                        .with(user("admin").roles("ADMIN")))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("Admin Panel")));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Logout
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /logout as authenticated user → redirect to /login?logout")
    void logout_AsAuthenticatedUser_RedirectsToLoginWithLogout() throws Exception {
        // Step 1: perform form login to obtain a session
        MvcResult loginResult = mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "user")
                        .param("password", "password")
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andReturn();

        // Step 2: extract the session cookie from the login response
        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);

        // Step 3: POST /logout with the session and a valid CSRF token
        // After logout the session should be invalidated and the browser redirected
        // to /login?logout.
        mockMvc.perform(post("/logout")
                        .session(session)
                        .with(csrf()))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrl("/login?logout"));
    }

    @Test
    @DisplayName("GET /dashboard after logout → redirected to /login (session invalid)")
    void afterLogout_AccessToDashboard_RedirectsToLogin() throws Exception {
        // Step 1: login
        MvcResult loginResult = mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .param("username", "user")
                        .param("password", "password")
                        .with(csrf()))
                .andReturn();

        MockHttpSession session = (MockHttpSession) loginResult.getRequest().getSession(false);

        // Step 2: logout
        mockMvc.perform(post("/logout")
                .session(session)
                .with(csrf()));

        // Step 3: try to access a protected page with the invalidated session.
        // Spring Security should treat this as unauthenticated and redirect to login.
        mockMvc.perform(get("/dashboard").session(session))
                .andExpect(status().is3xxRedirection())
                .andExpect(redirectedUrlPattern("**/login"));
    }
}
