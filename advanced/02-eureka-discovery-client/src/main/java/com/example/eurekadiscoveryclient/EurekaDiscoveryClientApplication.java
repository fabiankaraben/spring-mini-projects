package com.example.eurekadiscoveryclient;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cloud.client.discovery.EnableDiscoveryClient;

/**
 * Entry point for the Eureka Discovery Client application.
 *
 * <p><b>What is a Discovery Client?</b>
 * In a microservices architecture, services need to find each other dynamically
 * rather than using hard-coded IP addresses or hostnames (which change in
 * cloud/container environments). A discovery client solves this by:
 * <ol>
 *   <li><b>Registration</b>: on startup, this service POSTs its metadata
 *       (name, host, port, health-check URL) to the Eureka server.</li>
 *   <li><b>Heartbeat</b>: every 30 seconds, it sends a PUT request to the
 *       Eureka server to renew its lease and stay visible in the registry.</li>
 *   <li><b>Discovery</b>: it can look up other registered services by logical
 *       name (e.g. "payment-service") and get a list of healthy instances to
 *       call, enabling client-side load balancing.</li>
 *   <li><b>De-registration</b>: on graceful shutdown, it DELETEs its own
 *       registration so the registry removes it immediately without waiting
 *       for the lease expiry timeout.</li>
 * </ol>
 *
 * <p><b>Why {@code @EnableDiscoveryClient}?</b>
 * This annotation activates Spring Cloud's discovery client abstraction. With
 * the Eureka Client on the classpath, it registers a Eureka-specific
 * {@code DiscoveryClient} bean. The abstraction means the application code can
 * switch between Eureka, Consul, or Zookeeper by swapping dependencies without
 * changing the source code.
 *
 * <p><b>What happens at startup (with a running Eureka server)?</b>
 * <ol>
 *   <li>The app fetches the registry from Eureka and caches it locally.</li>
 *   <li>It registers itself under the name defined in {@code spring.application.name}.</li>
 *   <li>It starts a background heartbeat thread to renew the registration.</li>
 * </ol>
 *
 * <p><b>Standalone / test mode:</b>
 * When no Eureka server is available (e.g. during unit tests), the client is
 * configured to run in standalone mode: registration and registry fetching are
 * disabled, so the application still starts without errors.
 */
@SpringBootApplication
@EnableDiscoveryClient
public class EurekaDiscoveryClientApplication {

    public static void main(String[] args) {
        SpringApplication.run(EurekaDiscoveryClientApplication.class, args);
    }
}
