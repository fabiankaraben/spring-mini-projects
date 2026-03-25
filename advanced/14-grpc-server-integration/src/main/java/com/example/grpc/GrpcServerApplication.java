package com.example.grpc;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the gRPC Server Integration mini-project.
 *
 * <p>This Spring Boot application starts two servers concurrently:
 * <ul>
 *   <li>An HTTP server on port {@code 8080} (Spring MVC + Spring Actuator).</li>
 *   <li>A gRPC server on port {@code 9090} (Netty, via net.devh:grpc-server-spring-boot-starter).</li>
 * </ul>
 *
 * <p>The gRPC server is configured automatically by the starter. Any class annotated
 * with {@link net.devh.boot.grpc.server.service.GrpcService} is discovered, instantiated
 * as a Spring bean, and registered as a gRPC service implementation.
 *
 * <p>The HTTP port exposes:
 * <ul>
 *   <li>{@code GET /actuator/health} — liveness/readiness probe for Docker and Kubernetes.</li>
 *   <li>{@code GET /actuator/info}   — application metadata.</li>
 * </ul>
 */
@SpringBootApplication
public class GrpcServerApplication {

    public static void main(String[] args) {
        SpringApplication.run(GrpcServerApplication.class, args);
    }
}
