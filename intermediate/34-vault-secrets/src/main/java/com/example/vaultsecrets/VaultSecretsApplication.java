package com.example.vaultsecrets;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Vault Secrets Spring Boot application.
 *
 * <p>This application demonstrates how to integrate Spring Boot with
 * HashiCorp Vault via Spring Cloud Vault to store and retrieve secrets
 * (credentials) securely at runtime.</p>
 *
 * <h2>Key concepts covered</h2>
 * <ul>
 *   <li>Spring Cloud Vault Config: fetches secrets from Vault at startup
 *       and injects them as standard Spring properties</li>
 *   <li>KV v2 secrets engine: the standard Vault key-value store used to
 *       hold arbitrary key-value pairs such as database passwords, API keys, etc.</li>
 *   <li>Token authentication: the simplest Vault auth method; the app presents
 *       a static token to authenticate with Vault</li>
 *   <li>REST API: exposes endpoints to write secrets to Vault and read them back,
 *       demonstrating a typical "secrets manager" usage pattern</li>
 *   <li>Credential metadata: stored in an H2 (in-memory) database via Spring Data JPA,
 *       keeping a registry of secret paths without ever storing the secret values</li>
 * </ul>
 *
 * <h2>Security model</h2>
 * <p>Vault is the single source of truth for sensitive values.
 * The application never writes secret values to the relational database;
 * only the Vault path and a human-readable description are stored there.</p>
 */
@SpringBootApplication
public class VaultSecretsApplication {

    public static void main(String[] args) {
        SpringApplication.run(VaultSecretsApplication.class, args);
    }
}
