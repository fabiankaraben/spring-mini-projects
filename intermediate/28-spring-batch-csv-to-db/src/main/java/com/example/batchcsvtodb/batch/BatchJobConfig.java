package com.example.batchcsvtodb.batch;

import com.example.batchcsvtodb.model.Employee;
import jakarta.persistence.EntityManagerFactory;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.Step;
import org.springframework.batch.core.job.builder.JobBuilder;
import org.springframework.batch.core.launch.support.RunIdIncrementer;
import org.springframework.batch.core.repository.JobRepository;
import org.springframework.batch.core.step.builder.StepBuilder;
import org.springframework.batch.item.database.JpaItemWriter;
import org.springframework.batch.item.database.builder.JpaItemWriterBuilder;
import org.springframework.batch.item.file.FlatFileItemReader;
import org.springframework.batch.item.file.builder.FlatFileItemReaderBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.transaction.PlatformTransactionManager;

/**
 * Spring Batch configuration class.
 *
 * <p>Defines all the building blocks of the batch job:
 * <ul>
 *   <li><strong>Reader</strong> – {@code FlatFileItemReader} reads the CSV line by line.</li>
 *   <li><strong>Processor</strong> – {@code EmployeeItemProcessor} validates / transforms rows.</li>
 *   <li><strong>Writer</strong> – {@code JpaItemWriter} persists valid rows to PostgreSQL.</li>
 *   <li><strong>Step</strong> – wires reader → processor → writer with chunk-oriented processing.</li>
 *   <li><strong>Job</strong> – wraps the step; each execution gets a unique run-id so the same
 *       CSV can be re-imported after fixing bad data.</li>
 * </ul>
 *
 * <p>The path to the CSV file is configurable via {@code batch.csv-file-path} in
 * {@code application.yml} (default: {@code classpath:data/employees.csv}).
 */
@Configuration
public class BatchJobConfig {

    /**
     * Path to the CSV input file.
     * Can be overridden at runtime with the {@code BATCH_CSV_FILE_PATH} environment variable
     * or the {@code batch.csv-file-path} application property.
     *
     * <p>Accepts Spring resource notation:
     * <ul>
     *   <li>{@code classpath:data/employees.csv} – file bundled inside the JAR.</li>
     *   <li>{@code file:/data/employees.csv} – file on the host file system (useful in Docker).</li>
     * </ul>
     */
    @Value("${batch.csv-file-path:classpath:data/employees.csv}")
    private Resource csvResource;

    /**
     * Chunk size – the number of items processed and written in a single transaction.
     *
     * <p>After every {@code CHUNK_SIZE} items the current transaction is committed and
     * the progress is recorded in the Spring Batch meta-data tables. If the job fails
     * mid-way, it can restart from the last committed chunk.
     */
    private static final int CHUNK_SIZE = 10;

    /**
     * Creates the {@code FlatFileItemReader} that reads the CSV file line by line.
     *
     * <p>Configuration highlights:
     * <ul>
     *   <li>{@code linesToSkip(1)} – skips the header row.</li>
     *   <li>{@code names(...)} – maps CSV columns to {@link EmployeeCsvRow} bean properties
     *       via {@code BeanWrapperFieldSetMapper}.</li>
     *   <li>The delimiter defaults to a comma ({@code ,}).</li>
     * </ul>
     *
     * @return configured {@code FlatFileItemReader}
     */
    @Bean
    public FlatFileItemReader<EmployeeCsvRow> employeeItemReader() {
        return new FlatFileItemReaderBuilder<EmployeeCsvRow>()
                .name("employeeCsvItemReader")
                // Point the reader at the configured CSV resource
                .resource(csvResource)
                // Skip the header line (first_name,last_name,email,department,salary,hire_date)
                .linesToSkip(1)
                // Map each line's fields to the EmployeeCsvRow bean using its property names
                .delimited()
                .names("firstName", "lastName", "email", "department", "salary", "hireDate")
                .targetType(EmployeeCsvRow.class)
                .build();
    }

    /**
     * Creates the {@code JpaItemWriter} that persists a chunk of {@link Employee} entities
     * in a single JPA/Hibernate flush.
     *
     * <p>{@code JpaItemWriter} calls {@code EntityManager.persist()} or
     * {@code EntityManager.merge()} for each entity, then flushes at the end of the chunk.
     * The surrounding Spring Batch chunk transaction ensures atomicity.
     *
     * @param emf the JPA {@code EntityManagerFactory} auto-configured by Spring Boot
     * @return configured {@code JpaItemWriter}
     */
    @Bean
    public JpaItemWriter<Employee> employeeItemWriter(EntityManagerFactory emf) {
        return new JpaItemWriterBuilder<Employee>()
                .entityManagerFactory(emf)
                .build();
    }

    /**
     * Creates the batch {@code Step} using a chunk-oriented processing model.
     *
     * <p>The step processes {@code CHUNK_SIZE} items per transaction:
     * <ol>
     *   <li>Read up to {@code CHUNK_SIZE} items from the reader.</li>
     *   <li>Pass each item through the processor (null results are filtered out = skip).</li>
     *   <li>Write the surviving items with the JPA writer in a single transaction commit.</li>
     * </ol>
     *
     * @param jobRepository       Spring Batch infrastructure for persisting step execution state
     * @param transactionManager  platform transaction manager for the chunk transactions
     * @param reader              the CSV item reader
     * @param processor           the validation / transformation processor
     * @param writer              the JPA item writer
     * @return configured {@code Step}
     */
    @Bean
    public Step importEmployeesStep(JobRepository jobRepository,
                                    PlatformTransactionManager transactionManager,
                                    FlatFileItemReader<EmployeeCsvRow> reader,
                                    EmployeeItemProcessor processor,
                                    JpaItemWriter<Employee> writer) {
        return new StepBuilder("importEmployeesStep", jobRepository)
                // Chunk-oriented: read CHUNK_SIZE items per transaction
                .<EmployeeCsvRow, Employee>chunk(CHUNK_SIZE, transactionManager)
                .reader(reader)
                .processor(processor)
                .writer(writer)
                .build();
    }

    /**
     * Creates the batch {@code Job} that wraps the import step.
     *
     * <p>{@link RunIdIncrementer} appends a unique {@code run.id} parameter to each
     * {@code JobParameters} instance, allowing the same job to be launched multiple times
     * (Spring Batch prevents re-running a job with identical parameters unless it failed).
     *
     * @param jobRepository the Spring Batch {@code JobRepository}
     * @param step          the single import step
     * @return configured {@code Job}
     */
    @Bean
    public Job importEmployeesJob(JobRepository jobRepository, Step step) {
        return new JobBuilder("importEmployeesJob", jobRepository)
                // Each run gets a unique run.id so the same CSV can be imported multiple times
                .incrementer(new RunIdIncrementer())
                .flow(step)
                .end()
                .build();
    }
}
