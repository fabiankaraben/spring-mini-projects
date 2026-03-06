package com.example.sessionmanagement;

import jakarta.servlet.http.HttpSession;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller for managing user session data.
 * This controller provides endpoints to store and retrieve user-specific information
 * in the HttpSession, demonstrating session management in Spring Boot.
 */
@RestController
@RequestMapping("/api/session")
public class SessionController {

    /**
     * Sets the user's name in the session.
     * This endpoint stores the provided user name in the HttpSession,
     * making it available across subsequent requests from the same user.
     *
     * @param name the user's name to store in the session
     * @param session the HttpSession object for storing data
     * @return ResponseEntity with success message
     */
    @PostMapping("/user")
    public ResponseEntity<String> setUserName(@RequestParam String name, HttpSession session) {
        // Store the user name in the session
        session.setAttribute("user", name);

        // Return a success response
        return ResponseEntity.ok("User name '" + name + "' stored in session.");
    }

    /**
     * Retrieves the user's name from the session.
     * This endpoint fetches the user name stored in the HttpSession.
     * If no user name is found, returns a not found response.
     *
     * @param session the HttpSession object for retrieving data
     * @return ResponseEntity with the user name or not found message
     */
    @GetMapping("/user")
    public ResponseEntity<String> getUserName(HttpSession session) {
        // Retrieve the user name from the session
        String userName = (String) session.getAttribute("user");

        if (userName != null) {
            // Return the user name if found
            return ResponseEntity.ok("Hello, " + userName + "!");
        } else {
            // Return not found if no user name in session
            return ResponseEntity.notFound().build();
        }
    }
}
