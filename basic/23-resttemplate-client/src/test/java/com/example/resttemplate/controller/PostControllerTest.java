package com.example.resttemplate.controller;

import com.example.resttemplate.dto.Post;
import com.example.resttemplate.service.PostClientService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Sliced integration test for the PostController.
 * 
 * @WebMvcTest starts only the web layer components (controllers), and we mock
 *             the underlying service layer.
 */
@WebMvcTest(PostController.class)
class PostControllerTest {

    @Autowired
    private MockMvc mockMvc;

    // We use @MockitoBean to replace the service with a Mock in the Spring Context
    // Note: @MockitoBean is the replacement for the deprecated @MockBean
    @MockitoBean
    private PostClientService postClientService;

    @Test
    void shouldReturnAllPosts() throws Exception {
        // Given
        List<Post> mockedPosts = List.of(
                new Post(1L, 1L, "Title 1", "Body 1"),
                new Post(1L, 2L, "Title 2", "Body 2"));
        given(postClientService.getAllPosts()).willReturn(mockedPosts);

        // When & Then
        mockMvc.perform(get("/api/client/posts")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.size()").value(2))
                .andExpect(jsonPath("$[0].title").value("Title 1"));
    }

    @Test
    void shouldReturnPostById() throws Exception {
        // Given
        Post mockedPost = new Post(1L, 5L, "Specific Title", "Specific Body");
        given(postClientService.getPostById(5L)).willReturn(mockedPost);

        // When & Then
        mockMvc.perform(get("/api/client/posts/5")
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(5))
                .andExpect(jsonPath("$.title").value("Specific Title"));
    }

    @Test
    void shouldCreatePost() throws Exception {
        // Given
        Post responsePost = new Post(1L, 101L, "New Title", "New Body");

        given(postClientService.createPost(any(Post.class))).willReturn(responsePost);

        String payload = """
                {
                  "userId": 1,
                  "title": "New Title",
                  "body": "New Body"
                }
                """;

        // When & Then
        mockMvc.perform(post("/api/client/posts")
                .contentType(MediaType.APPLICATION_JSON)
                .content(payload)
                .accept(MediaType.APPLICATION_JSON))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(101))
                .andExpect(jsonPath("$.title").value("New Title"));
    }
}
