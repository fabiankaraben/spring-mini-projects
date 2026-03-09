package com.example.oauth2loginclient;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the OAuth2 Login Client mini-project.
 *
 * <p>This application demonstrates how to integrate Spring Security's OAuth2
 * Authorization Code flow so users can log in via GitHub or Google. After a
 * successful OAuth2 callback the authenticated user's profile is persisted to a
 * PostgreSQL database and a JSON summary is returned to the caller.</p>
 *
 * <p>Key concepts covered:
 * <ul>
 *   <li>OAuth2 Authorization Code Grant (RFC 6749 §4.1)</li>
 *   <li>Spring Security {@code OAuth2LoginConfigurer} and
 *       {@code OAuth2UserService}</li>
 *   <li>Custom {@code OAuth2UserService} to map provider attributes to a local
 *       {@code AppUser} entity</li>
 *   <li>JPA persistence of OAuth2 user profiles</li>
 * </ul>
 * </p>
 */
@SpringBootApplication
public class OAuth2LoginClientApplication {

	public static void main(String[] args) {
		SpringApplication.run(OAuth2LoginClientApplication.class, args);
	}
}
