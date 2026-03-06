package com.example.simplefilter;

import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * A simple javax.servlet.Filter implementation that demonstrates low-level request manipulation.
 * This filter logs incoming HTTP requests and adds a custom header to the response.
 *
 * The filter is automatically registered with Spring Boot due to the @Component annotation.
 */
@Component
public class LoggingFilter implements Filter {

    private static final Logger logger = LoggerFactory.getLogger(LoggingFilter.class);

    /**
     * Initializes the filter. This method is called once when the filter is loaded.
     * @param filterConfig the filter configuration
     * @throws ServletException if an error occurs during initialization
     */
    @Override
    public void init(FilterConfig filterConfig) throws ServletException {
        logger.info("LoggingFilter initialized");
    }

    /**
     * Processes the request and response. This is where the main filter logic resides.
     * Logs the request method and URI, then adds a custom header to the response.
     * @param request the servlet request
     * @param response the servlet response
     * @param chain the filter chain
     * @throws IOException if an I/O error occurs
     * @throws ServletException if a servlet error occurs
     */
    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;
        HttpServletResponse httpResponse = (HttpServletResponse) response;

        // Log the incoming request
        logger.info("Incoming request: {} {}", httpRequest.getMethod(), httpRequest.getRequestURI());

        // Add a custom header to the response
        httpResponse.setHeader("X-Simple-Filter", "Processed");

        // Continue the filter chain
        chain.doFilter(request, response);
    }

    /**
     * Destroys the filter. This method is called once when the filter is unloaded.
     */
    @Override
    public void destroy() {
        logger.info("LoggingFilter destroyed");
    }
}
