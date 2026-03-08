package com.example.simplefilter;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Main entry point for the Simple Filter application.
 * 
 * Spring Boot will automatically scan for components (including our filter)
 * in this package and sub-packages.
 */
@SpringBootApplication
public class SimpleFilterApplication {

	public static void main(String[] args) {
		SpringApplication.run(SimpleFilterApplication.class, args);
	}

}
