package com.example.sessionmanagement;

import jakarta.servlet.http.HttpSession;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Map;
import java.util.Enumeration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SessionControllerTest {

    @Mock
    private HttpSession session;

    @Test
    void testSetSessionAttribute() {
        SessionController controller = new SessionController();
        String result = controller.setSessionAttribute("username", "john_doe", session);
        
        verify(session).setAttribute("username", "john_doe");
        assertEquals("Session attribute set: username = john_doe", result);
    }

    @Test
    void testGetSessionAttribute_Found() {
        SessionController controller = new SessionController();
        when(session.getAttribute("username")).thenReturn("john_doe");
        
        String result = controller.getSessionAttribute("username", session);
        
        assertEquals("Value for username: john_doe", result);
    }

    @Test
    void testGetSessionAttribute_NotFound() {
        SessionController controller = new SessionController();
        when(session.getAttribute("missing")).thenReturn(null);
        
        String result = controller.getSessionAttribute("missing", session);
        
        assertEquals("No attribute found for key: missing", result);
    }

    @Test
    void testGetAllAttributes() {
        SessionController controller = new SessionController();
        
        // Mocking getAttributeNames() which returns Enumeration
        Enumeration<String> attributeNames = Collections.enumeration(Collections.singletonList("user"));
        when(session.getAttributeNames()).thenReturn(attributeNames);
        when(session.getAttribute("user")).thenReturn("alice");
        when(session.getId()).thenReturn("12345");

        Map<String, Object> result = controller.getAllAttributes(session);
        
        assertTrue(result.containsKey("user"));
        assertEquals("alice", result.get("user"));
        assertEquals("12345", result.get("sessionId"));
    }

    @Test
    void testInvalidateSession() {
        SessionController controller = new SessionController();
        String result = controller.invalidateSession(session);
        
        verify(session).invalidate();
        assertEquals("Session invalidated.", result);
    }
}
