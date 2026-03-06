package com.example.simplefilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.FilterConfig;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import java.io.IOException;

import static org.mockito.Mockito.*;

/**
 * Unit tests for the LoggingFilter class.
 * Uses JUnit 5 and Mockito to test the filter's behavior.
 */
class LoggingFilterTest {

    @Mock
    private FilterConfig filterConfig;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    private LoggingFilter loggingFilter;

    /**
     * Sets up the test environment before each test.
     * Initializes mocks and the filter instance.
     */
    @BeforeEach
    void setUp() {
        MockitoAnnotations.openMocks(this);
        loggingFilter = new LoggingFilter();
    }

    /**
     * Tests the init method of the filter.
     * Verifies that the filter can be initialized without errors.
     */
    @Test
    void testInit() throws Exception {
        // Act
        loggingFilter.init(filterConfig);

        // Assert - no exception thrown
    }

    /**
     * Tests the doFilter method.
     * Verifies that the filter logs the request, adds the custom header, and continues the chain.
     */
    @Test
    void testDoFilter() throws IOException, jakarta.servlet.ServletException {
        // Arrange
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/test");

        // Act
        loggingFilter.doFilter(request, response, filterChain);

        // Assert
        verify(response).setHeader("X-Simple-Filter", "Processed");
        verify(filterChain).doFilter(request, response);
    }

    /**
     * Tests the destroy method.
     * Verifies that the filter can be destroyed without errors.
     */
    @Test
    void testDestroy() {
        // Act
        loggingFilter.destroy();

        // Assert - no exception thrown
    }
}
