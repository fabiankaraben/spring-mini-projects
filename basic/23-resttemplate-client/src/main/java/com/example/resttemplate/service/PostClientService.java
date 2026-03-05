package com.example.resttemplate.service;

import com.example.resttemplate.dto.Post;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.util.Arrays;
import java.util.List;

/**
 * Service responsible for interacting with the external JSONPlaceholder API
 * using RestTemplate.
 */
@Service
public class PostClientService {

    private final RestTemplate restTemplate;
    // The base URL of the external API
    private static final String BASE_URL = "https://jsonplaceholder.typicode.com/posts";

    public PostClientService(org.springframework.boot.web.client.RestTemplateBuilder builder) {
        this.restTemplate = builder.build();
    }

    /**
     * Fetches all posts from the external API via a GET request.
     * 
     * @return List of posts
     */
    public List<Post> getAllPosts() {
        // RestTemplate's getForObject is a simple way to execute a GET request
        // and automatically unmarshal the response body into an array of objects.
        Post[] postsArray = restTemplate.getForObject(BASE_URL, Post[].class);
        return postsArray != null ? Arrays.asList(postsArray) : List.of();
    }

    /**
     * Fetches a specific post by ID.
     * 
     * @param id The ID of the post
     * @return The fetched post
     */
    public Post getPostById(Long id) {
        // Using url variables (the second argument) allows safe inclusion of parameters
        // in the URL
        String url = BASE_URL + "/{id}";
        return restTemplate.getForObject(url, Post.class, id);
    }

    /**
     * Creates a new post via a POST request.
     * 
     * @param post The post data to create
     * @return The created post as returned by the API
     */
    public Post createPost(Post post) {
        // postForEntity sends a POST request with the given object as the request body.
        // It returns a ResponseEntity containing the status code, headers, and the
        // unmarshaled body.
        ResponseEntity<Post> response = restTemplate.postForEntity(BASE_URL, post, Post.class);
        return response.getBody();
    }
}
