package com.example.devtools;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main application class for the DevTools demo.
 * 
 * This application is a standard Spring Boot application.
 * The spring-boot-devtools dependency in pom.xml enables:
 * 1. Automatic Restart: The application automatically restarts when files on the classpath change.
 * 2. LiveReload: The browser automatically refreshes when resources change (requires a browser extension).
 * 3. Property Defaults: Disables caching for templates (Thymeleaf, etc.) to see changes immediately.
 */
@SpringBootApplication
public class DevToolsApplication {

	public static void main(String[] args) {
		SpringApplication.run(DevToolsApplication.class, args);
	}

}
