package com.example.webclient_basic.controller;

import com.example.webclient_basic.dto.User;
import com.example.webclient_basic.service.UserWebClientService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST controller exposing endpoints that use the WebClient service.
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserWebClientService userService;

    public UserController(UserWebClientService userService) {
        this.userService = userService;
    }

    /**
     * Endpoint to fetch all users.
     * 
     * @return List of Users
     */
    @GetMapping
    public List<User> getAllUsers() {
        return userService.getAllUsers();
    }

    /**
     * Endpoint to fetch a single user by id.
     * 
     * @param id The ID to fetch
     * @return User
     */
    @GetMapping("/{id}")
    public User getUserById(@PathVariable Long id) {
        return userService.getUserById(id);
    }
}
