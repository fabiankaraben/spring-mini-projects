package com.example.batchcsvtodb;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Entry point for the Spring Batch CSV-to-DB mini-project.
 *
 * <p>This application demonstrates how to use Spring Batch to:
 * <ul>
 *   <li>Read rows from a CSV file using {@code FlatFileItemReader}</li>
 *   <li>Validate and transform each row using a custom {@code ItemProcessor}</li>
 *   <li>Persist valid rows into a PostgreSQL table using {@code JpaItemWriter}</li>
 *   <li>Skip (and log) invalid rows without stopping the whole job</li>
 * </ul>
 *
 * <p>The batch job can be triggered via a REST endpoint
 * ({@code POST /api/batch/jobs/import-employees}) and its results can be
 * queried through additional endpoints on the same controller.
 */
@SpringBootApplication
public class SpringBatchCsvToDbApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpringBatchCsvToDbApplication.class, args);
    }
}
