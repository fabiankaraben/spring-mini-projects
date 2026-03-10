package com.example.batchcsvtodb.controller;

import com.example.batchcsvtodb.model.Employee;
import com.example.batchcsvtodb.repository.EmployeeRepository;
import org.springframework.batch.core.Job;
import org.springframework.batch.core.JobExecution;
import org.springframework.batch.core.JobParameters;
import org.springframework.batch.core.JobParametersBuilder;
import org.springframework.batch.core.JobParametersInvalidException;
import org.springframework.batch.core.launch.JobLauncher;
import org.springframework.batch.core.repository.JobExecutionAlreadyRunningException;
import org.springframework.batch.core.repository.JobInstanceAlreadyCompleteException;
import org.springframework.batch.core.repository.JobRestartException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

/**
 * REST controller that exposes endpoints for triggering and monitoring batch jobs,
 * and for querying the imported employee data.
 *
 * <p><strong>Endpoints:</strong>
 * <ul>
 *   <li>{@code POST /api/batch/jobs/import-employees} – launch the CSV import job.</li>
 *   <li>{@code GET  /api/employees} – list all imported employees.</li>
 *   <li>{@code GET  /api/employees/{id}} – get a single employee by ID.</li>
 *   <li>{@code GET  /api/employees/department/{dept}} – filter by department.</li>
 * </ul>
 */
@RestController
@RequestMapping("/api")
public class BatchJobController {

    /**
     * Spring Batch {@code JobLauncher} – responsible for submitting a job
     * with a given set of parameters for asynchronous or synchronous execution.
     */
    private final JobLauncher jobLauncher;

    /**
     * The {@code importEmployeesJob} bean defined in {@code BatchJobConfig}.
     * Injected here so the controller can launch it on demand.
     */
    private final Job importEmployeesJob;

    /**
     * Repository used to query the persisted employee records after the batch job runs.
     */
    private final EmployeeRepository employeeRepository;

    public BatchJobController(JobLauncher jobLauncher,
                              Job importEmployeesJob,
                              EmployeeRepository employeeRepository) {
        this.jobLauncher        = jobLauncher;
        this.importEmployeesJob = importEmployeesJob;
        this.employeeRepository = employeeRepository;
    }

    // ── Batch job endpoints ───────────────────────────────────────────────────

    /**
     * Triggers the {@code importEmployeesJob} batch job.
     *
     * <p>A unique {@code run.id} timestamp is added to the job parameters so the same
     * job can be re-launched multiple times (Spring Batch normally blocks re-runs of
     * jobs that have already completed with the same parameters).
     *
     * <p>The job runs synchronously in the default configuration, meaning this endpoint
     * blocks until the job finishes.  The response body contains the final batch status
     * ({@code COMPLETED}, {@code FAILED}, etc.) and the counts of read / written / skipped items.
     *
     * <p><strong>cURL example:</strong>
     * <pre>
     *   curl -X POST http://localhost:8080/api/batch/jobs/import-employees
     * </pre>
     *
     * @return {@code 200 OK} with job summary, or {@code 500} on launch failure
     */
    @PostMapping("/batch/jobs/import-employees")
    public ResponseEntity<Map<String, Object>> runImportJob() {
        try {
            // Build job parameters – the run.id makes each execution unique
            JobParameters params = new JobParametersBuilder()
                    .addLong("run.id", System.currentTimeMillis())
                    .toJobParameters();

            // Launch the job; blocks until completion in synchronous mode
            JobExecution execution = jobLauncher.run(importEmployeesJob, params);

            // Extract step-level counters from the first (and only) step execution
            var stepExecution = execution.getStepExecutions().iterator().next();

            // Build a human-readable response body
            Map<String, Object> response = Map.of(
                    "jobName",       execution.getJobInstance().getJobName(),
                    "jobExecutionId", execution.getId(),
                    "status",        execution.getStatus().toString(),
                    "readCount",     stepExecution.getReadCount(),
                    "writeCount",    stepExecution.getWriteCount(),
                    "skipCount",     stepExecution.getProcessSkipCount(),
                    "startTime",     execution.getStartTime() != null
                                         ? execution.getStartTime().toString() : "N/A",
                    "endTime",       execution.getEndTime() != null
                                         ? execution.getEndTime().toString() : "N/A"
            );

            return ResponseEntity.ok(response);

        } catch (JobExecutionAlreadyRunningException |
                 JobRestartException |
                 JobInstanceAlreadyCompleteException |
                 JobParametersInvalidException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(Map.of("error", e.getMessage()));
        }
    }

    // ── Employee query endpoints ──────────────────────────────────────────────

    /**
     * Returns all employees stored in the database.
     *
     * <p><strong>cURL example:</strong>
     * <pre>
     *   curl http://localhost:8080/api/employees
     * </pre>
     *
     * @return list of all {@link Employee} entities; empty array if none exist
     */
    @GetMapping("/employees")
    public List<Employee> getAllEmployees() {
        return employeeRepository.findAll();
    }

    /**
     * Returns a single employee by their database ID.
     *
     * <p><strong>cURL example:</strong>
     * <pre>
     *   curl http://localhost:8080/api/employees/1
     * </pre>
     *
     * @param id the employee's primary key
     * @return {@code 200 OK} with the employee, or {@code 404 Not Found}
     */
    @GetMapping("/employees/{id}")
    public ResponseEntity<Employee> getEmployeeById(@PathVariable Long id) {
        return employeeRepository.findById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Returns all employees belonging to a specific department.
     *
     * <p><strong>cURL example:</strong>
     * <pre>
     *   curl http://localhost:8080/api/employees/department/Engineering
     * </pre>
     *
     * @param department the department name (case-sensitive)
     * @return list of employees in that department; empty array if none
     */
    @GetMapping("/employees/department/{department}")
    public List<Employee> getEmployeesByDepartment(@PathVariable String department) {
        return employeeRepository.findByDepartment(department);
    }

    /**
     * Returns the total count of employees currently stored in the database.
     *
     * <p><strong>cURL example:</strong>
     * <pre>
     *   curl http://localhost:8080/api/employees/count
     * </pre>
     *
     * @return JSON object with the {@code count} field
     */
    @GetMapping("/employees/count")
    public Map<String, Long> getEmployeeCount() {
        return Map.of("count", employeeRepository.count());
    }
}
