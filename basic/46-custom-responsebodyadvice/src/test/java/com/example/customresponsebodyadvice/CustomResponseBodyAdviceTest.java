package com.example.customresponsebodyadvice;

import org.junit.jupiter.api.Test;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit tests for CustomResponseBodyAdvice.
 * Tests the logic of intercepting and modifying response bodies.
 */
class CustomResponseBodyAdviceTest {

    private final CustomResponseBodyAdvice advice = new CustomResponseBodyAdvice();

    /**
     * Test that supports method returns true for Map return types.
     */
    @Test
    void beforeBodyWrite_shouldAddProcessedAtTimestamp() throws Exception {
        // Given
        Map<String, Object> body = new HashMap<>();
        body.put("message", "test");
        Method method = TestMethods.class.getMethod("mapMethod");
        MethodParameter methodParameter = new MethodParameter(method, -1);

        // When
        Map<String, Object> result = advice.beforeBodyWrite(
                body,
                methodParameter,
                MediaType.APPLICATION_JSON,
                MappingJackson2HttpMessageConverter.class,
                null,
                null
        );

        // Then
        assertNotNull(result);
        assertEquals("test", result.get("message"));
        assertTrue(result.containsKey("processedAt"));
        assertNotNull(result.get("processedAt"));
        assertTrue(result.get("processedAt") instanceof String);
    }

    /**
     * Test that beforeBodyWrite handles null body gracefully.
     */
    @Test
    void beforeBodyWrite_shouldHandleNullBody() throws Exception {
        // Given
        Method method = TestMethods.class.getMethod("mapMethod");
        MethodParameter methodParameter = new MethodParameter(method, -1);

        // When
        Map<String, Object> result = advice.beforeBodyWrite(
                null,
                methodParameter,
                MediaType.APPLICATION_JSON,
                MappingJackson2HttpMessageConverter.class,
                null,
                null
        );

        // Then
        assertNull(result);
    }

    /**
     * Dummy class with methods for testing MethodParameter creation.
     */
    static class TestMethods {
        public Map<String, Object> mapMethod() { return null; }
        public String stringMethod() { return null; }
    }
}
