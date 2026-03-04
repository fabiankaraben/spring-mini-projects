package com.example.simple_logger_middleware.interceptor;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * A simple HandlerInterceptor that logs the HTTP method, request URI, and
 * duration.
 */
@Component
public class RequestLoggingInterceptor implements HandlerInterceptor {

    private static final Logger logger = LoggerFactory.getLogger(RequestLoggingInterceptor.class);

    // Key used to store the start time in the request attributes
    private static final String START_TIME_ATTRIBUTE = "startTime";

    /**
     * Intercepts the request before it is handled by the controller.
     * We capture the current time and store it in the request attributes.
     */
    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        long startTime = System.currentTimeMillis();
        request.setAttribute(START_TIME_ATTRIBUTE, startTime);
        return true; // Continue processing the request
    }

    /**
     * Called after the complete request has finished (after the view has been
     * rendered if any).
     * We retrieve the start time, calculate the duration, and log the details.
     */
    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler,
            Exception ex) {
        Long startTime = (Long) request.getAttribute(START_TIME_ATTRIBUTE);

        if (startTime != null) {
            long duration = System.currentTimeMillis() - startTime;
            // Log the HTTP method, request URI, and processing duration
            logger.info("Method: {} | Path: {} | Duration: {} ms",
                    request.getMethod(), request.getRequestURI(), duration);
        }
    }
}
