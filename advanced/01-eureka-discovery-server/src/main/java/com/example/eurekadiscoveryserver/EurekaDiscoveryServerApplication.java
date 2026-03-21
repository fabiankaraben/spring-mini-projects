package com.example.eurekadiscoveryserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.netflix.eureka.server.EnableEurekaServer;

/**
 * Entry point for the Eureka Discovery Server application.
 *
 * <p><b>What is a Service Registry?</b>
 * In a microservices architecture, services need to find each other without
 * hard-coded hostnames or ports (which change in cloud/container environments).
 * A service registry solves this:
 * <ol>
 *   <li><b>Registration</b>: each microservice registers itself with the registry
 *       on startup (providing its name, host, port, and health-check URL).</li>
 *   <li><b>Discovery</b>: a client looking for, say, "order-service" asks the
 *       registry and receives a list of healthy instances.</li>
 *   <li><b>De-registration</b>: on shutdown, services deregister. The registry
 *       also removes instances that stop sending heartbeats.</li>
 * </ol>
 *
 * <p><b>Why Netflix Eureka?</b>
 * Eureka (originally built by Netflix, donated to the community) is the most
 * widely used service registry in the Spring Cloud ecosystem. It provides:
 * <ul>
 *   <li>An HTTP REST API for registration and discovery.</li>
 *   <li>A built-in web dashboard for visualising registered services.</li>
 *   <li>A self-preservation mode that tolerates short network partitions.</li>
 *   <li>Client-side caching so services can still discover peers even if the
 *       registry temporarily goes down.</li>
 * </ul>
 *
 * <p><b>{@code @EnableEurekaServer}</b> activates the embedded Eureka server.
 * Under the hood it imports {@code EurekaServerAutoConfiguration} which:
 * <ul>
 *   <li>Starts the peer-aware instance registry (tracks all registered services).</li>
 *   <li>Registers the Eureka REST API servlets (Jersey) under {@code /eureka/*}.</li>
 *   <li>Exposes the Eureka dashboard at the root path {@code /}.</li>
 * </ul>
 */
@SpringBootApplication
@EnableEurekaServer
public class EurekaDiscoveryServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(EurekaDiscoveryServerApplication.class, args);
    }
}
