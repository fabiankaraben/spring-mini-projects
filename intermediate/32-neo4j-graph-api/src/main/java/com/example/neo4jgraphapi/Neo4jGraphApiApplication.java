package com.example.neo4jgraphapi;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Neo4j Graph API application.
 *
 * <p>This application demonstrates how to model and query a graph database
 * using Spring Data Neo4j. The domain models a simple social/knowledge graph
 * with Person nodes and Movie nodes connected via ACTED_IN, DIRECTED, and
 * FOLLOWS relationships.</p>
 */
@SpringBootApplication
public class Neo4jGraphApiApplication {

    public static void main(String[] args) {
        SpringApplication.run(Neo4jGraphApiApplication.class, args);
    }
}
