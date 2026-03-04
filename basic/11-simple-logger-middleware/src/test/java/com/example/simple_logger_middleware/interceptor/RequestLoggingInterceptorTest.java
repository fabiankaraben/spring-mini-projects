package com.example.simple_logger_middleware.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Unit tests using JUnit 5 and Mockito for the RequestLoggingInterceptor.
 */
@ExtendWith(MockitoExtension.class)
class RequestLoggingInterceptorTest {

    @InjectMocks
    private RequestLoggingInterceptor interceptor;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    private final Object handler = new Object();

    /**
     * Set up before each test case.
     */
    @BeforeEach
    void setUp() {
    }

    /**
     * Tests that the preHandle method stores the start time in the request
     * attribute.
     */
    @Test
    void testPreHandleStoresStartTime() {
        boolean result = interceptor.preHandle(request, response, handler);

        // The preHandle must return true to continue processing
        assertTrue(result);

        // We verify that an attribute "startTime" is being set with a long value.
        verify(request, times(1)).setAttribute(eq("startTime"), anyLong());
    }

    /**
     * Tests that the afterCompletion method correctly calculates the duration and
     * doesn't throw errors.
     * Note: We cannot easily verify the logger output without setting up appenders,
     * but we can verify interactions with the request object.
     */
    @Test
    void testAfterCompletionRetrievesStartTime() throws Exception {
        // Simulate a request starting 200 milliseconds earlier
        long startTime = System.currentTimeMillis() - 200;
        when(request.getAttribute("startTime")).thenReturn(startTime);
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/hello");

        interceptor.afterCompletion(request, response, handler, null);

        // Verify the start time attribute was fetched, and method and URI were accessed
        verify(request, times(1)).getAttribute("startTime");
        verify(request, times(1)).getMethod();
        verify(request, times(1)).getRequestURI();
    }
}
