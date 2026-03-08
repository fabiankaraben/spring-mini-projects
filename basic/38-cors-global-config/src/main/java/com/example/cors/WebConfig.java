package com.example.cors;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Global Configuration for CORS (Cross-Origin Resource Sharing).
 * 
 * CORS is a security mechanism that allows a web page from one domain or Origin
 * to access resources with a different domain, protocol, or port.
 * 
 * By default, web browsers block cross-origin requests for security reasons.
 * This configuration enables specific origins to access our API.
 */
@Configuration
public class WebConfig {

	/**
	 * Configures CORS settings globally for the application.
	 * 
	 * @return WebMvcConfigurer with CORS mappings
	 */
	@Bean
	public WebMvcConfigurer corsConfigurer() {
		return new WebMvcConfigurer() {
			@Override
			public void addCorsMappings(CorsRegistry registry) {
				// Apply CORS settings to all paths ("/**")
				registry.addMapping("/**")
						// Allow requests only from this specific origin (e.g., a frontend app running on port 9090)
						.allowedOrigins("http://localhost:9090")
						// Allow specific HTTP methods
						.allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS")
						// Allow all headers to be sent
						.allowedHeaders("*")
						// Allow credentials (cookies, authorization headers) to be sent
						.allowCredentials(true);
			}
		};
	}
}
