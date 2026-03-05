package com.example.resttemplate.controller;

import com.example.resttemplate.dto.Post;
import com.example.resttemplate.service.PostClientService;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller that exposes endpoints to interact with our
 * PostClientService.
 * This acts as a proxy/facade to the external JSONPlaceholder API.
 */
@RestController
@RequestMapping("/api/client/posts")
public class PostController {

    private final PostClientService postClientService;

    public PostController(PostClientService postClientService) {
        this.postClientService = postClientService;
    }

    /**
     * Endpoint to get all posts from the external API.
     */
    @GetMapping
    public List<Post> getAllPosts() {
        return postClientService.getAllPosts();
    }

    /**
     * Endpoint to get a specific post from the external API.
     */
    @GetMapping("/{id}")
    public Post getPostById(@PathVariable Long id) {
        return postClientService.getPostById(id);
    }

    /**
     * Endpoint to create a post in the external API.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Post createPost(@RequestBody Post post) {
        return postClientService.createPost(post);
    }
}
