package com.example.camel.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Strongly-typed configuration properties for the Apache Camel Pipes application.
 *
 * <p>All values are read from {@code application.yml} under the {@code app} prefix and can
 * be overridden at runtime via environment variables (Spring Boot relaxed binding):
 * <pre>
 *   APP_PIPELINE_PRIORITY_THRESHOLD=500
 *   APP_PIPELINE_VAT_RATE=0.20
 *   APP_OUTPUT_DIR=/tmp/camel-orders
 * </pre>
 *
 * <p>Nested static classes mirror the YAML hierarchy:
 * <pre>
 *   app:
 *     pipeline:
 *       priority-threshold: 300
 *       vat-rate: 0.21
 *     output:
 *       dir: ./output/orders
 *     broker:
 *       url: tcp://localhost:61616
 * </pre>
 */
@Component
@ConfigurationProperties(prefix = "app")
public class AppProperties {

    /** Pipeline-level tuning parameters. */
    private Pipeline pipeline = new Pipeline();

    /** Output file-system settings. */
    private Output output = new Output();

    /** Message broker connection settings. */
    private Broker broker = new Broker();

    // ── Getters / setters ────────────────────────────────────────────────────

    public Pipeline getPipeline() { return pipeline; }
    public void setPipeline(Pipeline pipeline) { this.pipeline = pipeline; }

    public Output getOutput() { return output; }
    public void setOutput(Output output) { this.output = output; }

    public Broker getBroker() { return broker; }
    public void setBroker(Broker broker) { this.broker = broker; }

    // ── Nested config classes ─────────────────────────────────────────────────

    /**
     * Pipeline processing parameters.
     */
    public static class Pipeline {

        /**
         * Orders whose computed {@code totalAmount} is ≥ this value (in currency units)
         * are classified as {@link com.example.camel.domain.OrderPriority#PRIORITY}.
         * Default: 300.
         */
        private double priorityThreshold = 300.0;

        /**
         * Value-Added Tax rate applied to compute {@code vatAmount}.
         * Example: 0.21 = 21 %.  Default: 0.21.
         */
        private double vatRate = 0.21;

        public double getPriorityThreshold() { return priorityThreshold; }
        public void setPriorityThreshold(double priorityThreshold) {
            this.priorityThreshold = priorityThreshold;
        }

        public double getVatRate() { return vatRate; }
        public void setVatRate(double vatRate) { this.vatRate = vatRate; }
    }

    /**
     * File-system output settings for the persistence stage.
     */
    public static class Output {

        /**
         * Directory where processed order JSON files are written.
         * The file component will create this directory if it does not exist.
         * Default: {@code ./output/orders}.
         */
        private String dir = "./output/orders";

        public String getDir() { return dir; }
        public void setDir(String dir) { this.dir = dir; }
    }

    /**
     * JMS broker connection settings.
     */
    public static class Broker {

        /**
         * ActiveMQ Artemis broker URL.
         * Overridden by Docker Compose / Testcontainers with the container's mapped port.
         * Default: {@code tcp://localhost:61616}.
         */
        private String url = "tcp://localhost:61616";

        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
    }
}
