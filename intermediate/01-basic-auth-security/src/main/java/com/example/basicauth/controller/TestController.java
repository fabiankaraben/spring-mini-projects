package com.example.basicauth.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST Controller providing endpoints to test Basic Authentication.
 */
@RestController
@RequestMapping("/api")
public class TestController {

    /**
     * A public endpoint accessible without authentication.
     *
     * @return a welcome message
     */
    @GetMapping("/public")
    public ResponseEntity<String> getPublicData() {
        return ResponseEntity.ok("This is a public endpoint. Anyone can access it.");
    }

    /**
     * A secured endpoint accessible only to authenticated users.
     *
     * @param authentication the current authentication context
     * @return a message including the authenticated user's name
     */
    @GetMapping("/user/me")
    public ResponseEntity<String> getUserData(Authentication authentication) {
        return ResponseEntity.ok("Hello, " + authentication.getName() + "! You have accessed a secured endpoint.");
    }

    /**
     * A secured endpoint accessible only to users with the ADMIN role.
     *
     * @param authentication the current authentication context
     * @return a message for admins
     */
    @GetMapping("/admin/data")
    public ResponseEntity<String> getAdminData(Authentication authentication) {
        return ResponseEntity.ok("Hello Admin " + authentication.getName() + "! You have accessed an admin-only endpoint.");
    }
}
