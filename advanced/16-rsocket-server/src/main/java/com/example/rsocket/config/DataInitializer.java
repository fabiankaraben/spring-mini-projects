package com.example.rsocket.config;

import com.example.rsocket.domain.StockQuote;
import com.example.rsocket.repository.StockQuoteRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Seeds the in-memory H2 database with sample stock quotes on application startup.
 *
 * <p>Using a {@link CommandLineRunner} bean is the idiomatic Spring Boot way to
 * run initialization code after the application context is fully loaded and all
 * beans (including the JPA repositories) are ready.
 *
 * <p>Why seed data?
 *   The Request-Response and Request-Stream routes need at least one known quote
 *   per symbol to work correctly out of the box. Without seeding, clients would
 *   receive empty responses until they submit quotes themselves.
 *
 * <p>Symbols seeded:
 * <ul>
 *   <li>AAPL  — Apple Inc.</li>
 *   <li>GOOG  — Alphabet Inc.</li>
 *   <li>MSFT  — Microsoft Corporation</li>
 *   <li>AMZN  — Amazon.com Inc.</li>
 *   <li>TSLA  — Tesla Inc.</li>
 * </ul>
 */
@Configuration
public class DataInitializer {

    /**
     * CommandLineRunner bean that inserts sample stock quotes on startup.
     *
     * <p>Each quote is given a timestamp slightly in the past (1 minute ago)
     * so that the data appears as a real-world "last recorded quote" rather
     * than an exact now() value.
     *
     * @param quoteRepository Spring Data JPA repository for stock quotes
     * @return CommandLineRunner that seeds the database
     */
    @Bean
    public CommandLineRunner seedStockQuotes(StockQuoteRepository quoteRepository) {
        return args -> {
            // Only seed if the database is empty (idempotent initialization).
            if (quoteRepository.count() > 0) {
                return;
            }

            Instant baseTime = Instant.now().minus(1, ChronoUnit.MINUTES);

            // Apple Inc. — strong positive day
            quoteRepository.save(new StockQuote(
                    "AAPL", "Apple Inc.",
                    215.50, +3.25, +1.53,
                    72_345_100L,
                    baseTime.minus(60, ChronoUnit.SECONDS)));

            // Alphabet Inc. — slight negative day
            quoteRepository.save(new StockQuote(
                    "GOOG", "Alphabet Inc.",
                    178.30, -1.10, -0.61,
                    18_204_500L,
                    baseTime.minus(55, ChronoUnit.SECONDS)));

            // Microsoft Corporation — slight positive day
            quoteRepository.save(new StockQuote(
                    "MSFT", "Microsoft Corporation",
                    420.75, +2.15, +0.51,
                    24_567_800L,
                    baseTime.minus(50, ChronoUnit.SECONDS)));

            // Amazon.com Inc. — moderate positive day
            quoteRepository.save(new StockQuote(
                    "AMZN", "Amazon.com Inc.",
                    198.40, +4.60, +2.37,
                    35_891_200L,
                    baseTime.minus(45, ChronoUnit.SECONDS)));

            // Tesla Inc. — volatile negative day
            quoteRepository.save(new StockQuote(
                    "TSLA", "Tesla Inc.",
                    248.90, -8.45, -3.28,
                    95_123_400L,
                    baseTime.minus(40, ChronoUnit.SECONDS)));
        };
    }
}
