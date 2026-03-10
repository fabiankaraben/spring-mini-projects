package com.example.batchschedulers.batch.config;

import com.example.batchschedulers.batch.processor.ReportGenerationItemProcessor;
import com.example.batchschedulers.model.Product;
import com.example.batchschedulers.model.ProductReport;
import com.example.batchschedulers.repository.ProductRepository;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.core.step.tasklet.TaskletStep;
import org.springframework.batch.item.database.JpaCursorItemReader;
import org.springframework.batch.item.database.JpaItemWriter;
import org.springframework.batch.item.database.builder.JpaCursorItemReaderBuilder;
import org.springframework.batch.item.database.builder.JpaItemWriterBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.transaction.PlatformTransactionManager;

import java.util.List;

/**
 * Spring Batch configuration for the {@code reportGenerationJob}.
 *
 * <p>This job generates category-level summary reports for all products. It runs
 * as two sequential steps:
 * <ol>
 *   <li><strong>initReportProcessorStep</strong> (Tasklet) – loads all products and
 *       pre-computes the per-category aggregates ({@code productCount}, {@code totalStock},
 *       {@code averagePrice}, {@code lowStockCount}). These statistics are injected into
 *       the {@link ReportGenerationItemProcessor} bean before the chunk step starts.</li>
 *   <li><strong>reportGenerationStep</strong> (Chunk) – reads all products again
 *       through a {@code JpaCursorItemReader}, lets the processor emit exactly one
 *       {@link ProductReport} per category (filtering duplicates), and writes the
 *       reports via {@code JpaItemWriter}.</li>
 * </ol>
 *
 * <p>Job structure:
 * <pre>
 * reportGenerationJob
 *   ├── initReportProcessorStep (Tasklet) – pre-compute category aggregates
 *   └── reportGenerationStep   (Chunk)   – read products → emit one report per category
 * </pre>
 */
@Configuration
public class ReportGenerationJobConfig {

    /** Number of products / report rows processed per transaction. */
    private static final int CHUNK_SIZE = 20;

    /**
     * The product repository is used inside the initialisation tasklet to load
     * the full product list for aggregate computation.
     */
    private final ProductRepository productRepository;

    /**
     * The processor bean is shared between the initialisation tasklet (for
     * {@code initializeCategoryStats}) and the chunk step (for item processing).
     */
    private final ReportGenerationItemProcessor reportGenerationItemProcessor;

    public ReportGenerationJobConfig(ProductRepository productRepository,
                                     ReportGenerationItemProcessor reportGenerationItemProcessor) {
        this.productRepository = productRepository;
        this.reportGenerationItemProcessor = reportGenerationItemProcessor;
    }

    /**
     * Creates a {@code JpaCursorItemReader} that streams all {@link Product} entities
     * for the report generation step.
     *
     * @param emf the JPA {@code EntityManagerFactory}
     * @return configured reader
     */
    @Bean
    public JpaCursorItemReader<Product> reportGenerationItemReader(EntityManagerFactory emf) {
        return new JpaCursorItemReaderBuilder<Product>()
                .name("reportGenerationItemReader")
                .entityManagerFactory(emf)
                // Order by category so that the first occurrence of each category is
                // encountered in a predictable order (processor emits one report per category)
                .queryString("SELECT p FROM Product p ORDER BY p.category, p.id")
                .build();
    }

    /**
     * Creates the {@code JpaItemWriter} for persisting {@link ProductReport} entities.
     *
     * @param emf the JPA {@code EntityManagerFactory}
     * @return configured writer
     */
    @Bean
    public JpaItemWriter<ProductReport> reportGenerationItemWriter(EntityManagerFactory emf) {
        return new JpaItemWriterBuilder<ProductReport>()
                .entityManagerFactory(emf)
                .build();
    }

    /**
     * Creates a Tasklet-based step that loads all products and initialises
     * the {@link ReportGenerationItemProcessor} with pre-computed category statistics.
     *
     * <p>A {@code Tasklet} is appropriate here because this is a single unit of work
     * (load all products, compute aggregates) rather than a chunk-oriented operation.
     * It runs before {@code reportGenerationStep} so the processor has valid stats
     * when the chunk step starts.
     *
     * @param jobRepository      Spring Batch job repository
     * @param transactionManager platform transaction manager
     * @return configured tasklet step
     */
    @Bean
    public TaskletStep initReportProcessorStep(JobRepository jobRepository,
                                               PlatformTransactionManager transactionManager) {
        return new StepBuilder("initReportProcessorStep", jobRepository)
                // A Tasklet is a single callable unit of work; it runs once per step execution.
                // RepeatStatus.FINISHED tells Spring Batch the tasklet is done.
                .tasklet((contribution, chunkContext) -> {
                    List<Product> allProducts = productRepository.findAll();
                    // Pre-compute per-category aggregates and inject them into the processor
                    reportGenerationItemProcessor.initializeCategoryStats(allProducts);
                    return org.springframework.batch.repeat.RepeatStatus.FINISHED;
                }, transactionManager)
                .build();
    }

    /**
     * Creates the chunk-oriented step that reads products and writes one
     * {@link ProductReport} per category.
     *
     * @param jobRepository                 Spring Batch job repository
     * @param transactionManager            platform transaction manager
     * @param reportGenerationItemReader    the cursor reader
     * @param reportGenerationItemWriter    the JPA writer
     * @return configured step
     */
    @Bean
    public Step reportGenerationStep(JobRepository jobRepository,
                                     PlatformTransactionManager transactionManager,
                                     JpaCursorItemReader<Product> reportGenerationItemReader,
                                     JpaItemWriter<ProductReport> reportGenerationItemWriter) {
        return new StepBuilder("reportGenerationStep", jobRepository)
                .<Product, ProductReport>chunk(CHUNK_SIZE, transactionManager)
                .reader(reportGenerationItemReader)
                .processor(reportGenerationItemProcessor)
                .writer(reportGenerationItemWriter)
                .build();
    }

    /**
     * Creates the {@code reportGenerationJob} bean.
     *
     * <p>The job runs two steps in sequence:
     * <ol>
     *   <li>{@code initReportProcessorStep} – pre-computes aggregates</li>
     *   <li>{@code reportGenerationStep} – produces {@link ProductReport} rows</li>
     * </ol>
     *
     * @param jobRepository           the Spring Batch {@code JobRepository}
     * @param initReportProcessorStep the initialisation tasklet step
     * @param reportGenerationStep    the chunk step
     * @return the configured job
     */
    @Bean
    public Job reportGenerationJob(JobRepository jobRepository,
                                   TaskletStep initReportProcessorStep,
                                   Step reportGenerationStep) {
        return new JobBuilder("reportGenerationJob", jobRepository)
                .incrementer(new RunIdIncrementer())
                // Step 1: init → Step 2: generate reports
                .start(initReportProcessorStep)
                .next(reportGenerationStep)
                .build();
    }
}
