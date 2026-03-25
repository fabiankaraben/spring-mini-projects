package com.example.grpcclient;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the gRPC Client Interaction mini-project.
 *
 * <p>This Spring Boot application demonstrates the CLIENT side of gRPC communication:
 * a REST API gateway that consumes two internal gRPC microservices using
 * @GrpcClient-injected stubs from the net.devh grpc-client-spring-boot-starter.
 *
 * <p>What starts when this application launches:
 * <ol>
 *   <li>Spring Boot HTTP server (port 8080) — REST API + Actuator health/info.</li>
 *   <li>gRPC Netty server — hosts both OrderService and InventoryService.
 *       By default the single gRPC server starts on port 9090, but we configure it
 *       to listen on 9091 for the combined services.</li>
 *   <li>gRPC client channels — the net.devh starter creates managed channels for
 *       "order-service" and "inventory-service" based on application.yml config,
 *       and injects blocking stubs into OrderGatewayService via @GrpcClient.</li>
 *   <li>H2 in-memory database — seeded with sample inventory data by DataInitializer.</li>
 * </ol>
 *
 * <p>Key educational points of this project:
 * <ul>
 *   <li>How @GrpcClient works — channel creation, stub injection, lifecycle management.</li>
 *   <li>Consuming gRPC unary RPCs from a Spring bean (createOrder, getOrder, checkStock).</li>
 *   <li>Consuming gRPC server-streaming RPCs (listOrders, listInventory — Iterator pattern).</li>
 *   <li>Multi-service orchestration — one REST request → two gRPC calls with compensation.</li>
 *   <li>Error propagation — gRPC StatusRuntimeException → GrpcServiceException → HTTP error.</li>
 * </ul>
 */
@SpringBootApplication
public class GrpcClientInteractionApplication {

    /**
     * Application entry point. Delegates to {@link SpringApplication#run} which:
     * <ol>
     *   <li>Creates the Spring application context.</li>
     *   <li>Starts the embedded Tomcat HTTP server.</li>
     *   <li>Starts the Netty gRPC server (via net.devh starter auto-configuration).</li>
     *   <li>Initializes gRPC client channels (via net.devh starter auto-configuration).</li>
     *   <li>Runs all {@code ApplicationRunner} and {@code CommandLineRunner} beans (DataInitializer).</li>
     * </ol>
     *
     * @param args command-line arguments (not used in this demo)
     */
    public static void main(String[] args) {
        SpringApplication.run(GrpcClientInteractionApplication.class, args);
    }
}
