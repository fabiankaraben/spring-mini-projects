package com.example.configurationserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.config.server.EnableConfigServer;

/**
 * Main entry point for the Configuration Server application.
 * <p>
 * The {@code @EnableConfigServer} annotation is the key component here.
 * It tells Spring Boot to treat this application as a Spring Cloud Config Server,
 * enabling it to serve configuration properties to other applications.
 * </p>
 */
@EnableConfigServer
@SpringBootApplication
public class ConfigServerApplication {

	public static void main(String[] args) {
		SpringApplication.run(ConfigServerApplication.class, args);
	}

}
