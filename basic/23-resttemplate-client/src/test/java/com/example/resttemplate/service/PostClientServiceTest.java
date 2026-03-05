package com.example.resttemplate.service;

import com.example.resttemplate.dto.Post;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.client.RestClientTest;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;

import java.net.URI;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withCreatedEntity;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

/**
 * Unit test for the PostClientService class using JUnit 5 and RestClientTest.
 * This sets up a MockRestServiceServer which is the idiomatic way to test
 * RestTemplate clients in Spring Boot.
 */
@RestClientTest(PostClientService.class)
class PostClientServiceTest {

    @Autowired
    private PostClientService postClientService;

    @Autowired
    private MockRestServiceServer server;

    @Test
    void shouldGetAllPosts() {
        // Given
        String mockJsonResponse = """
                [
                  { "userId": 1, "id": 1, "title": "Title 1", "body": "Body 1" },
                  { "userId": 2, "id": 2, "title": "Title 2", "body": "Body 2" }
                ]
                """;

        server.expect(requestTo("https://jsonplaceholder.typicode.com/posts"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(mockJsonResponse, MediaType.APPLICATION_JSON));

        // When
        List<Post> result = postClientService.getAllPosts();

        // Then
        assertThat(result).hasSize(2);
        assertThat(result.get(0).title()).isEqualTo("Title 1");
        server.verify();
    }

    @Test
    void shouldGetPostById() {
        // Given
        String mockJsonResponse = """
                { "userId": 1, "id": 5, "title": "Title 5", "body": "Body 5" }
                """;

        server.expect(requestTo("https://jsonplaceholder.typicode.com/posts/5"))
                .andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess(mockJsonResponse, MediaType.APPLICATION_JSON));

        // When
        Post result = postClientService.getPostById(5L);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(5L);
        assertThat(result.title()).isEqualTo("Title 5");
        server.verify();
    }

    @Test
    void shouldCreatePost() throws Exception {
        // Given
        Post requestPost = new Post(1L, null, "New Title", "New Body");
        String mockJsonResponse = """
                { "userId": 1, "id": 101, "title": "New Title", "body": "New Body" }
                """;

        server.expect(requestTo("https://jsonplaceholder.typicode.com/posts"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withCreatedEntity(new URI("https://jsonplaceholder.typicode.com/posts/101"))
                        .body(mockJsonResponse)
                        .contentType(MediaType.APPLICATION_JSON));

        // When
        Post result = postClientService.createPost(requestPost);

        // Then
        assertThat(result).isNotNull();
        assertThat(result.id()).isEqualTo(101L);
        assertThat(result.title()).isEqualTo("New Title");
        server.verify();
    }
}
