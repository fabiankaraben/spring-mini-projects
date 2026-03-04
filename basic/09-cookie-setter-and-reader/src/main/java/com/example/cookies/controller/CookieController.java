package com.example.cookies.controller;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller demonstrating how to set and read cookies in Spring Boot.
 * This is an educational controller explaining the concepts of @CookieValue
 * and HttpServletResponse to set cookies.
 */
@RestController
@RequestMapping("/api/cookies")
public class CookieController {

    /**
     * Endpoint to set a cookie in the HTTP response.
     * We use HttpServletResponse to manually add a new Cookie object.
     *
     * @param value    the value to set in the cookie
     * @param response the HttpServletResponse used to append the cookie to the HTTP
     *                 headers
     * @return a message confirming the cookie was set successfully
     */
    @PostMapping("/set")
    public ResponseEntity<String> setCookie(@RequestParam("value") String value, HttpServletResponse response) {
        // Create a new cookie with the name "user_preference" and the provided value
        Cookie cookie = new Cookie("user_preference", value);

        // Security: HttpOnly flag prevents scripts (like JavaScript) from accessing the
        // cookie,
        // which helps mitigate Cross-Site Scripting (XSS) attacks.
        cookie.setHttpOnly(true);

        // The path determines where this cookie is valid. "/" means the entire
        // application.
        cookie.setPath("/");

        // Expiration time: We set it to expire in 7 days (represented in seconds)
        cookie.setMaxAge(7 * 24 * 60 * 60);

        // Add the cookie to the response headers
        response.addCookie(cookie);

        return ResponseEntity.ok("Cookie 'user_preference' has been set with value: " + value);
    }

    /**
     * Endpoint to read a cookie from the incoming HTTP request.
     * We use the @CookieValue annotation to bind the cookie's value directly to a
     * method parameter.
     *
     * @param cookieValue the value of 'user_preference'. If it's missing,
     *                    defaultValue is used.
     * @return a message displaying the cookie's value
     */
    @GetMapping("/read")
    public ResponseEntity<String> readCookie(
            @CookieValue(name = "user_preference", defaultValue = "No cookie found") String cookieValue) {
        // Return the value read from the cookie
        return ResponseEntity.ok("The read value of 'user_preference' is: " + cookieValue);
    }
}
