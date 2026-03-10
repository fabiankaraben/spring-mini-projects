package com.example.batchschedulers.batch.config;

import com.example.batchschedulers.batch.processor.PriceRefreshItemProcessor;
import com.example.batchschedulers.model.Product;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.database.JpaCursorItemReader;
import org.springframework.batch.item.database.JpaItemWriter;
import org.springframework.batch.item.database.builder.JpaCursorItemReaderBuilder;
import org.springframework.batch.item.database.builder.JpaItemWriterBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Spring Batch configuration for the {@code priceRefreshJob}.
 *
 * <p>This job simulates periodically refreshing product prices from an external
 * pricing source. In production this would call an external API; here we apply
 * a configurable percentage adjustment to every product's current price.
 *
 * <p>Job structure:
 * <pre>
 * priceRefreshJob
 *   └── priceRefreshStep
 *         ├── Reader:    JpaCursorItemReader – streams all products from DB
 *         ├── Processor: PriceRefreshItemProcessor – applies price adjustment
 *         └── Writer:    JpaItemWriter – merges updated products back to DB
 * </pre>
 *
 * <p>A {@link RunIdIncrementer} is attached so that the scheduler can launch
 * the same job multiple times (Spring Batch would otherwise reject a second
 * launch with identical {@code JobParameters}).
 */
@Configuration
public class PriceRefreshJobConfig {

    /**
     * Number of products processed and written per transaction (chunk size).
     *
     * <p>After every {@code CHUNK_SIZE} products, the transaction is committed and
     * Spring Batch updates the step-execution meta-data. Smaller values reduce
     * memory pressure but increase transaction overhead; 20 is a reasonable default
     * for a small demo dataset.
     */
    private static final int CHUNK_SIZE = 20;

    /**
     * Creates a {@code JpaCursorItemReader} that streams all {@link Product} entities
     * from the database using a JPA query cursor.
     *
     * <p>A cursor-based reader is preferred over a paging reader for batch jobs
     * because it reads the result set in a single database round-trip without
     * needing to manage page offsets. It is suitable when the full dataset fits in
     * the JVM heap.
     *
     * @param emf the JPA {@code EntityManagerFactory} auto-configured by Spring Boot
     * @return configured reader
     */
    @Bean
    public JpaCursorItemReader<Product> priceRefreshItemReader(EntityManagerFactory emf) {
        return new JpaCursorItemReaderBuilder<Product>()
                .name("priceRefreshItemReader")
                .entityManagerFactory(emf)
                // JPQL query – selects all products; ordering ensures deterministic processing
                .queryString("SELECT p FROM Product p ORDER BY p.id")
                .build();
    }

    /**
     * Creates the {@code JpaItemWriter} that merges the updated {@link Product}
     * entities back to PostgreSQL at the end of each chunk.
     *
     * <p>{@code JpaItemWriter} calls {@code EntityManager.merge()} for each entity
     * (since they already have an id) and then flushes at the end of the chunk
     * within the surrounding transaction.
     *
     * @param emf the JPA {@code EntityManagerFactory}
     * @return configured writer
     */
    @Bean
    public JpaItemWriter<Product> priceRefreshItemWriter(EntityManagerFactory emf) {
        return new JpaItemWriterBuilder<Product>()
                .entityManagerFactory(emf)
                .build();
    }

    /**
     * Creates the chunk-oriented step for the price refresh job.
     *
     * @param jobRepository      Spring Batch infrastructure for persisting step state
     * @param transactionManager platform transaction manager for chunk transactions
     * @param reader             the JPA cursor reader
     * @param processor          the price adjustment processor
     * @param writer             the JPA item writer
     * @return configured step
     */
    @Bean
    public Step priceRefreshStep(JobRepository jobRepository,
                                 PlatformTransactionManager transactionManager,
                                 JpaCursorItemReader<Product> priceRefreshItemReader,
                                 PriceRefreshItemProcessor processor,
                                 JpaItemWriter<Product> priceRefreshItemWriter) {
        return new StepBuilder("priceRefreshStep", jobRepository)
                .<Product, Product>chunk(CHUNK_SIZE, transactionManager)
                .reader(priceRefreshItemReader)
                .processor(processor)
                .writer(priceRefreshItemWriter)
                .build();
    }

    /**
     * Creates the {@code priceRefreshJob} bean.
     *
     * <p>{@link RunIdIncrementer} appends a unique {@code run.id} to the
     * {@code JobParameters} on each launch, allowing the scheduler to re-run
     * the job on every tick without Spring Batch rejecting it as a duplicate.
     *
     * @param jobRepository the Spring Batch {@code JobRepository}
     * @param priceRefreshStep the single step of this job
     * @return the configured job
     */
    @Bean
    public Job priceRefreshJob(JobRepository jobRepository, Step priceRefreshStep) {
        return new JobBuilder("priceRefreshJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(priceRefreshStep)
                .build();
    }
}
