package com.example.elasticlogging;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Elastic Stack Logging mini-project.
 *
 * <p>This application demonstrates how to configure a Spring Boot service to emit
 * <strong>structured JSON logs</strong> that are picked up by the Elastic Stack
 * (Elasticsearch + Logstash + Kibana).
 *
 * <h2>Log pipeline overview</h2>
 * <pre>
 *   Spring Boot app
 *       |  (writes JSON lines via logstash-logback-encoder)
 *       v
 *   /var/log/app/app.json    (mounted volume in Docker)
 *       |
 *       v
 *   Filebeat                 (tails the log file, forwards to Logstash)
 *       |
 *       v
 *   Logstash                 (parses, enriches, forwards to Elasticsearch)
 *       |
 *       v
 *   Elasticsearch            (indexes documents, exposes REST search API)
 *       |
 *       v
 *   Kibana                   (visualises and queries the log data in a browser)
 * </pre>
 *
 * <h2>Key libraries</h2>
 * <ul>
 *   <li><b>logstash-logback-encoder</b> – replaces Logback's default PatternLayout with a JSON
 *       encoder so every log line is a valid JSON object.</li>
 *   <li><b>SLF4J MDC</b> – thread-local key/value pairs (e.g. requestId, userId) that are
 *       automatically included in every JSON log event within the MDC scope.</li>
 *   <li><b>StructuredArguments</b> – adds structured key/value fields directly into the JSON
 *       log object without embedding them in the human-readable message string.</li>
 * </ul>
 */
@SpringBootApplication
public class ElasticStackLoggingApplication {

    private static final Logger log = LoggerFactory.getLogger(ElasticStackLoggingApplication.class);

    public static void main(String[] args) {
        SpringApplication.run(ElasticStackLoggingApplication.class, args);
        // This startup log will appear as a structured JSON event in the log file.
        log.info("Elastic Stack Logging application started successfully");
    }
}
