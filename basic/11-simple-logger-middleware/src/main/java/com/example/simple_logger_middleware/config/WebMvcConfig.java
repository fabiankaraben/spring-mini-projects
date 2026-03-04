package com.example.simple_logger_middleware.config;

import com.example.simple_logger_middleware.interceptor.RequestLoggingInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Configuration class meant to register HandlerInterceptors in the
 * application's
 * Web MVC context.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final RequestLoggingInterceptor requestLoggingInterceptor;

    // Constructor injection for our interceptor
    public WebMvcConfig(RequestLoggingInterceptor requestLoggingInterceptor) {
        this.requestLoggingInterceptor = requestLoggingInterceptor;
    }

    /**
     * Registers interceptors for requests.
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        // Here we add our intereceptor to the registry. Default behaviour applies to
        // all endpoints,
        // but path patterns can be specified if we only want logging on particular
        // routes.
        registry.addInterceptor(requestLoggingInterceptor);
    }
}
