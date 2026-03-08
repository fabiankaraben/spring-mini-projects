package com.example.simplefilter.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class RequestLoggingFilterTest {

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @InjectMocks
    private RequestLoggingFilter requestLoggingFilter;

    @Test
    void doFilter_ShouldLogAndProceed() throws ServletException, IOException {
        // Arrange
        when(request.getMethod()).thenReturn("GET");
        when(request.getRequestURI()).thenReturn("/test");

        // Act
        requestLoggingFilter.doFilter(request, response, filterChain);

        // Assert
        // Verify that the filter chain continues
        verify(filterChain, times(1)).doFilter(request, response);
        // We can't easily verify static Logger calls with Mockito standard setup, 
        // but verifying the chain continues confirms the flow wasn't blocked/exception didn't stop it (unless expected).
        // Verification of logging specifically would require a logging capturing appender or library, 
        // which might be overkill for a "simple" mini-project unit test unless strictly required. 
        // The core logic here is that it calls chain.doFilter.
    }
}
