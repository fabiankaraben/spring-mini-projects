package com.example.ratelimitingfilter.config;

import com.example.ratelimitingfilter.interceptor.RateLimitInterceptor;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Spring MVC configuration that registers the {@link RateLimitInterceptor}.
 *
 * <p>{@link WebMvcConfigurer} provides hook methods to customise the Spring MVC
 * infrastructure without replacing the default auto-configuration. Implementing
 * {@link #addInterceptors(InterceptorRegistry)} is the canonical way to register
 * custom {@link org.springframework.web.servlet.HandlerInterceptor} instances.
 *
 * <p>The interceptor is applied to all paths ({@code /**}) by default.
 * Actuator endpoints ({@code /actuator/**}) are excluded so that health checks
 * from load balancers or monitoring tools never consume tokens.
 */
@Configuration
public class WebMvcConfig implements WebMvcConfigurer {

    private final RateLimitInterceptor rateLimitInterceptor;

    public WebMvcConfig(RateLimitInterceptor rateLimitInterceptor) {
        this.rateLimitInterceptor = rateLimitInterceptor;
    }

    /**
     * Registers the rate-limit interceptor for all application paths, but
     * excludes the Actuator namespace so that health checks are never throttled.
     *
     * @param registry the interceptor registry provided by Spring MVC
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(rateLimitInterceptor)
                .addPathPatterns("/**")           // apply to everything …
                .excludePathPatterns("/actuator/**"); // … except actuator endpoints
    }
}
