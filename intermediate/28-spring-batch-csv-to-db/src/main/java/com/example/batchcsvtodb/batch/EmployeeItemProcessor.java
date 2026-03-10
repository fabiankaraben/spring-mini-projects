package com.example.batchcsvtodb.batch;

import com.example.batchcsvtodb.model.Employee;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.batch.item.ItemProcessor;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;

/**
 * Spring Batch {@code ItemProcessor} that validates and transforms a raw CSV row
 * ({@link EmployeeCsvRow}) into a persisted-ready {@link Employee} entity.
 *
 * <p><strong>Processing pipeline:</strong>
 * <ol>
 *   <li>Trim whitespace from all string fields.</li>
 *   <li>Validate that required fields (firstName, lastName, email, department) are non-blank.</li>
 *   <li>Validate that the email field contains an "@" character (basic format check).</li>
 *   <li>Parse and validate {@code salary} as a positive {@link BigDecimal}.</li>
 *   <li>Parse and validate {@code hireDate} as a {@link LocalDate} with format {@code yyyy-MM-dd}.</li>
 *   <li>Return {@code null} for any invalid row so Spring Batch skips it (null return = skip).</li>
 * </ol>
 *
 * <p>Every skipped row is logged at WARN level so operators can identify data quality issues
 * without stopping the entire job.
 */
@Component
public class EmployeeItemProcessor implements ItemProcessor<EmployeeCsvRow, Employee> {

    private static final Logger log = LoggerFactory.getLogger(EmployeeItemProcessor.class);

    /** Expected date format in the CSV hire_date column. */
    private static final DateTimeFormatter DATE_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd");

    /**
     * Validates and converts a single raw CSV row into an {@link Employee} entity.
     *
     * @param row the raw row read from the CSV file; never {@code null}
     * @return a valid {@link Employee} ready for persistence, or {@code null} to skip the row
     */
    @Override
    public Employee process(@NonNull EmployeeCsvRow row) {
        // Step 1 – Trim all string fields to remove leading/trailing whitespace
        String firstName  = trimOrEmpty(row.getFirstName());
        String lastName   = trimOrEmpty(row.getLastName());
        String email      = trimOrEmpty(row.getEmail());
        String department = trimOrEmpty(row.getDepartment());
        String salaryStr  = trimOrEmpty(row.getSalary());
        String hireDateStr = trimOrEmpty(row.getHireDate());

        // Step 2 – Validate required string fields
        if (firstName.isEmpty()) {
            log.warn("Skipping row – firstName is blank: {}", row);
            return null;
        }
        if (lastName.isEmpty()) {
            log.warn("Skipping row – lastName is blank: {}", row);
            return null;
        }
        if (email.isEmpty()) {
            log.warn("Skipping row – email is blank: {}", row);
            return null;
        }
        if (department.isEmpty()) {
            log.warn("Skipping row – department is blank: {}", row);
            return null;
        }

        // Step 3 – Basic email format validation (must contain "@")
        if (!email.contains("@")) {
            log.warn("Skipping row – invalid email format '{}': {}", email, row);
            return null;
        }

        // Step 4 – Parse salary as a positive BigDecimal
        BigDecimal salary;
        try {
            salary = new BigDecimal(salaryStr);
            if (salary.compareTo(BigDecimal.ZERO) <= 0) {
                log.warn("Skipping row – salary must be positive, got '{}': {}", salaryStr, row);
                return null;
            }
        } catch (NumberFormatException e) {
            log.warn("Skipping row – cannot parse salary '{}': {}", salaryStr, row);
            return null;
        }

        // Step 5 – Parse hire date using the expected yyyy-MM-dd format
        LocalDate hireDate;
        try {
            hireDate = LocalDate.parse(hireDateStr, DATE_FORMATTER);
        } catch (DateTimeParseException e) {
            log.warn("Skipping row – cannot parse hireDate '{}' (expected yyyy-MM-dd): {}", hireDateStr, row);
            return null;
        }

        // All validations passed – build and return the Employee entity
        Employee employee = new Employee(firstName, lastName, email, department, salary, hireDate);
        log.debug("Processed valid employee: {}", employee);
        return employee;
    }

    /**
     * Returns the trimmed value of {@code s}, or an empty string if {@code s} is {@code null}.
     *
     * @param s the string to trim
     * @return trimmed string, never {@code null}
     */
    private String trimOrEmpty(String s) {
        return s == null ? "" : s.trim();
    }
}
