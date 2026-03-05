package com.example.webclient_basic.service;

import com.example.webclient_basic.dto.User;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import java.util.List;

/**
 * Service that uses WebClient to fetch data synchronously.
 * Note: Although WebClient is reactive by nature, we can call .block() to get
 * the results synchronously.
 */
@Service
public class UserWebClientService {

    private final WebClient webClient;

    public UserWebClientService(WebClient jsonPlaceholderWebClient) {
        this.webClient = jsonPlaceholderWebClient;
    }

    /**
     * Fetches all users from the external API synchronously.
     * 
     * @return List of users
     */
    public List<User> getAllUsers() {
        return webClient.get()
                .uri("/users")
                .retrieve()
                .bodyToFlux(User.class)
                .collectList()
                .block(); // Blocking makes it synchronous waiting for the Flux
    }

    /**
     * Fetches a single user by ID synchronously.
     * 
     * @param id The user ID
     * @return User object
     */
    public User getUserById(Long id) {
        return webClient.get()
                .uri("/users/{id}", id)
                .retrieve()
                .bodyToMono(User.class)
                .block(); // Blocking makes it synchronous waiting for the Mono
    }
}
