package com.example.basicauth.controller;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests for TestController using Testcontainers and MockMvc.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Testcontainers
class TestControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>(
            "postgres:16-alpine"
    );

    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @BeforeAll
    static void beforeAll() {
        postgres.start();
    }

    @AfterAll
    static void afterAll() {
        postgres.stop();
    }

    @Test
    void publicEndpoint_ShouldBeAccessibleWithoutAuth() throws Exception {
        mockMvc.perform(get("/api/public"))
                .andExpect(status().isOk())
                .andExpect(content().string("This is a public endpoint. Anyone can access it."));
    }

    @Test
    void userEndpoint_WithoutAuth_ShouldReturnUnauthorized() throws Exception {
        mockMvc.perform(get("/api/user/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void userEndpoint_WithValidUserCredentials_ShouldReturnOk() throws Exception {
        mockMvc.perform(get("/api/user/me").with(httpBasic("user", "password")))
                .andExpect(status().isOk())
                .andExpect(content().string("Hello, user! You have accessed a secured endpoint."));
    }

    @Test
    void userEndpoint_WithValidAdminCredentials_ShouldReturnOk() throws Exception {
        mockMvc.perform(get("/api/user/me").with(httpBasic("admin", "admin123")))
                .andExpect(status().isOk())
                .andExpect(content().string("Hello, admin! You have accessed a secured endpoint."));
    }

    @Test
    void userEndpoint_WithInvalidCredentials_ShouldReturnUnauthorized() throws Exception {
        mockMvc.perform(get("/api/user/me").with(httpBasic("user", "wrongpassword")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminEndpoint_WithoutAuth_ShouldReturnUnauthorized() throws Exception {
        mockMvc.perform(get("/api/admin/data"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminEndpoint_WithUserCredentials_ShouldReturnForbidden() throws Exception {
        mockMvc.perform(get("/api/admin/data").with(httpBasic("user", "password")))
                .andExpect(status().isForbidden());
    }

    @Test
    void adminEndpoint_WithAdminCredentials_ShouldReturnOk() throws Exception {
        mockMvc.perform(get("/api/admin/data").with(httpBasic("admin", "admin123")))
                .andExpect(status().isOk())
                .andExpect(content().string("Hello Admin admin! You have accessed an admin-only endpoint."));
    }
}
