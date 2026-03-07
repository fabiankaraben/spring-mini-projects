package com.example.sessionmanagement;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/**
 * Controller to demonstrate session management.
 */
@RestController
@RequestMapping("/session")
public class SessionController {

    /**
     * Sets a value in the session.
     * @param key The key to store the value under.
     * @param value The value to store.
     * @param session The HTTP session.
     * @return A message indicating the value was set.
     */
    @PostMapping("/set")
    public String setSessionAttribute(@RequestParam String key, @RequestParam String value, HttpSession session) {
        session.setAttribute(key, value);
        return "Session attribute set: " + key + " = " + value;
    }

    /**
     * Retrieves a value from the session.
     * @param key The key to retrieve.
     * @param session The HTTP session.
     * @return The value, or a message if not found.
     */
    @GetMapping("/get")
    public String getSessionAttribute(@RequestParam String key, HttpSession session) {
        Object value = session.getAttribute(key);
        if (value != null) {
            return "Value for " + key + ": " + value;
        } else {
            return "No attribute found for key: " + key;
        }
    }

    /**
     * Lists all attributes in the current session.
     * @param session The HTTP session.
     * @return A map of all session attributes.
     */
    @GetMapping("/all")
    public Map<String, Object> getAllAttributes(HttpSession session) {
        Map<String, Object> attributes = new HashMap<>();
        session.getAttributeNames().asIterator().forEachRemaining(name -> 
            attributes.put(name, session.getAttribute(name))
        );
        attributes.put("sessionId", session.getId());
        return attributes;
    }

    /**
     * Invalidates the current session.
     * @param session The HTTP session.
     * @return A message indicating the session was invalidated.
     */
    @PostMapping("/invalidate")
    public String invalidateSession(HttpSession session) {
        session.invalidate();
        return "Session invalidated.";
    }
}
