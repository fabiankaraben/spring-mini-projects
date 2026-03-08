package com.example.demo;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main entry point for the Custom ResponseBodyAdvice application.
 * <p>
 * This Spring Boot application demonstrates how to use {@link org.springframework.web.servlet.mvc.method.annotation.ResponseBodyAdvice}
 * to intercept and modify API responses globally.
 * </p>
 */
@SpringBootApplication
public class DemoApplication {

	public static void main(String[] args) {
		SpringApplication.run(DemoApplication.class, args);
	}

}
