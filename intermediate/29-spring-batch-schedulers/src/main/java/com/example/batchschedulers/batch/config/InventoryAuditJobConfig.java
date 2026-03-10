package com.example.batchschedulers.batch.config;

import com.example.batchschedulers.batch.processor.InventoryAuditItemProcessor;
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
 * Spring Batch configuration for the {@code inventoryAuditJob}.
 *
 * <p>This job periodically inspects every product's stock level and updates the
 * {@code lowStock} flag and {@code lastAudited} timestamp. Products with a
 * {@code stockQuantity} below {@code Product.LOW_STOCK_THRESHOLD} are flagged.
 *
 * <p>Job structure:
 * <pre>
 * inventoryAuditJob
 *   └── inventoryAuditStep
 *         ├── Reader:    JpaCursorItemReader – streams all products from DB
 *         ├── Processor: InventoryAuditItemProcessor – sets lowStock flag
 *         └── Writer:    JpaItemWriter – persists updated flags back to DB
 * </pre>
 *
 * <p>Bean names use the prefix {@code inventoryAudit} to avoid conflicts with
 * the beans defined in {@link PriceRefreshJobConfig} and {@link ReportGenerationJobConfig}.
 */
@Configuration
public class InventoryAuditJobConfig {

    /** Number of products processed and written per transaction. */
    private static final int CHUNK_SIZE = 20;

    /**
     * Creates a {@code JpaCursorItemReader} that streams all {@link Product} entities
     * for inventory auditing.
     *
     * <p>Each bean has a unique name ({@code "inventoryAuditItemReader"}) so Spring
     * does not confuse it with the reader from {@link PriceRefreshJobConfig}.
     *
     * @param emf the JPA {@code EntityManagerFactory}
     * @return configured reader
     */
    @Bean
    public JpaCursorItemReader<Product> inventoryAuditItemReader(EntityManagerFactory emf) {
        return new JpaCursorItemReaderBuilder<Product>()
                .name("inventoryAuditItemReader")
                .entityManagerFactory(emf)
                .queryString("SELECT p FROM Product p ORDER BY p.id")
                .build();
    }

    /**
     * Creates the {@code JpaItemWriter} for persisting audited products.
     *
     * @param emf the JPA {@code EntityManagerFactory}
     * @return configured writer
     */
    @Bean
    public JpaItemWriter<Product> inventoryAuditItemWriter(EntityManagerFactory emf) {
        return new JpaItemWriterBuilder<Product>()
                .entityManagerFactory(emf)
                .build();
    }

    /**
     * Creates the chunk-oriented step for the inventory audit job.
     *
     * @param jobRepository          Spring Batch job repository
     * @param transactionManager     platform transaction manager
     * @param inventoryAuditItemReader  the cursor reader
     * @param processor              the audit processor
     * @param inventoryAuditItemWriter  the JPA writer
     * @return configured step
     */
    @Bean
    public Step inventoryAuditStep(JobRepository jobRepository,
                                   PlatformTransactionManager transactionManager,
                                   JpaCursorItemReader<Product> inventoryAuditItemReader,
                                   InventoryAuditItemProcessor processor,
                                   JpaItemWriter<Product> inventoryAuditItemWriter) {
        return new StepBuilder("inventoryAuditStep", jobRepository)
                .<Product, Product>chunk(CHUNK_SIZE, transactionManager)
                .reader(inventoryAuditItemReader)
                .processor(processor)
                .writer(inventoryAuditItemWriter)
                .build();
    }

    /**
     * Creates the {@code inventoryAuditJob} bean.
     *
     * @param jobRepository      the Spring Batch {@code JobRepository}
     * @param inventoryAuditStep the single step of this job
     * @return the configured job
     */
    @Bean
    public Job inventoryAuditJob(JobRepository jobRepository, Step inventoryAuditStep) {
        return new JobBuilder("inventoryAuditJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                .start(inventoryAuditStep)
                .build();
    }
}
