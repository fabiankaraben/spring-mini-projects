package com.example.distributedlock;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

import com.example.distributedlock.config.AppProperties;

/**
 * Entry point for the Distributed Lock Redis mini-project.
 *
 * <p>This application demonstrates how to prevent concurrent execution of the same
 * task across multiple application instances (or threads) using a Redis-backed
 * distributed lock provided by <strong>Redisson</strong>.
 *
 * <h2>Core concept</h2>
 * In a horizontally-scaled environment several JVM processes run simultaneously.
 * A plain Java {@code synchronized} block only protects one JVM, not the whole
 * cluster.  A Redis distributed lock uses a single shared Redis instance as the
 * coordination point:
 * <ul>
 *   <li>The first caller sets a Redis key (the lock) with an expiry (leaseTime).</li>
 *   <li>Subsequent callers see the key and either wait or return immediately.</li>
 *   <li>When the holder finishes it deletes the key, unblocking one waiter.</li>
 *   <li>If the holder crashes the key expires automatically, preventing dead-locks.</li>
 * </ul>
 *
 * <h2>How to run</h2>
 * <pre>
 *   docker compose up --build    # starts Redis + this application
 * </pre>
 *
 * <h2>How to test manually</h2>
 * Submit two tasks with the same key at the same time — the second request will
 * wait (or fail-fast) until the first one finishes:
 * <pre>
 *   curl -s -X POST http://localhost:8080/api/tasks \
 *        -H 'Content-Type: application/json' \
 *        -d '{"taskKey":"report-generation","payload":"Q4 data"}' &amp;
 *   curl -s -X POST http://localhost:8080/api/tasks \
 *        -H 'Content-Type: application/json' \
 *        -d '{"taskKey":"report-generation","payload":"Q4 data"}'
 * </pre>
 */
@SpringBootApplication
@EnableConfigurationProperties(AppProperties.class)
public class DistributedLockRedisApplication {

    public static void main(String[] args) {
        SpringApplication.run(DistributedLockRedisApplication.class, args);
    }
}
