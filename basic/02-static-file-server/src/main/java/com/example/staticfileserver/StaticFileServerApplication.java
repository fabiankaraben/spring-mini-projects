package com.example.staticfileserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main entry point for the Static File Server application.
 * Spring Boot relies on the @SpringBootApplication annotation
 * to trigger component scanning, auto-configuration, and property support.
 */
@SpringBootApplication
public class StaticFileServerApplication {

	/**
	 * The main method, serving as the entry point for the Java application.
	 * SpringApplication.run() starts the whole Spring framework,
	 * initializing the application context and embedded web server.
	 *
	 * @param args command-line arguments passed to the application
	 */
	public static void main(String[] args) {
		SpringApplication.run(StaticFileServerApplication.class, args);
	}

}
