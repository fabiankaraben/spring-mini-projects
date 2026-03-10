package com.example.batchcsvtodb.batch;

import com.example.batchcsvtodb.model.Employee;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link EmployeeItemProcessor}.
 *
 * <p>These tests exercise the validation and transformation logic in complete isolation –
 * no Spring context, no database, no network. Each test creates a fresh processor instance
 * (no shared state) and verifies a specific validation rule.
 *
 * <p>Spring Batch's chunk-oriented processing contract: when the processor returns
 * {@code null}, the item is filtered (skipped) and not forwarded to the writer. These
 * tests verify that every invalid input produces a {@code null} return value.
 */
@DisplayName("EmployeeItemProcessor – unit tests")
class EmployeeItemProcessorTest {

    /** The processor under test – new instance per test to avoid shared state. */
    private EmployeeItemProcessor processor;

    @BeforeEach
    void setUp() {
        // Create a fresh processor before each test
        processor = new EmployeeItemProcessor();
    }

    // ── Happy-path tests ──────────────────────────────────────────────────────

    @Test
    @DisplayName("should return a valid Employee entity when all fields are correct")
    void process_validRow_returnsEmployee() throws Exception {
        // Arrange – build a fully valid CSV row
        EmployeeCsvRow row = buildValidRow();

        // Act
        Employee result = processor.process(row);

        // Assert – result must not be null and all fields must be transformed correctly
        assertThat(result).isNotNull();
        assertThat(result.getFirstName()).isEqualTo("Alice");
        assertThat(result.getLastName()).isEqualTo("Johnson");
        assertThat(result.getEmail()).isEqualTo("alice.johnson@example.com");
        assertThat(result.getDepartment()).isEqualTo("Engineering");
        assertThat(result.getSalary()).isEqualByComparingTo(new BigDecimal("95000.00"));
        assertThat(result.getHireDate()).isEqualTo(LocalDate.of(2021, 3, 15));
    }

    @Test
    @DisplayName("should trim leading/trailing whitespace from all string fields")
    void process_rowWithWhitespace_trimmedCorrectly() throws Exception {
        // Arrange – fields have surrounding whitespace as can happen in messy CSV exports
        EmployeeCsvRow row = new EmployeeCsvRow();
        row.setFirstName("  Bob  ");
        row.setLastName("  Smith  ");
        row.setEmail("  bob.smith@example.com  ");
        row.setDepartment("  Finance  ");
        row.setSalary("  75000.00  ");
        row.setHireDate("  2022-06-01  ");

        // Act
        Employee result = processor.process(row);

        // Assert – trimming must have occurred
        assertThat(result).isNotNull();
        assertThat(result.getFirstName()).isEqualTo("Bob");
        assertThat(result.getLastName()).isEqualTo("Smith");
        assertThat(result.getEmail()).isEqualTo("bob.smith@example.com");
        assertThat(result.getDepartment()).isEqualTo("Finance");
    }

    // ── firstName validation tests ────────────────────────────────────────────

    @Test
    @DisplayName("should return null when firstName is blank")
    void process_blankFirstName_returnsNull() throws Exception {
        EmployeeCsvRow row = buildValidRow();
        row.setFirstName("   "); // whitespace only

        assertThat(processor.process(row)).isNull();
    }

    @Test
    @DisplayName("should return null when firstName is null")
    void process_nullFirstName_returnsNull() throws Exception {
        EmployeeCsvRow row = buildValidRow();
        row.setFirstName(null);

        assertThat(processor.process(row)).isNull();
    }

    // ── lastName validation tests ─────────────────────────────────────────────

    @Test
    @DisplayName("should return null when lastName is blank")
    void process_blankLastName_returnsNull() throws Exception {
        EmployeeCsvRow row = buildValidRow();
        row.setLastName("");

        assertThat(processor.process(row)).isNull();
    }

    @Test
    @DisplayName("should return null when lastName is null")
    void process_nullLastName_returnsNull() throws Exception {
        EmployeeCsvRow row = buildValidRow();
        row.setLastName(null);

        assertThat(processor.process(row)).isNull();
    }

    // ── email validation tests ────────────────────────────────────────────────

    @Test
    @DisplayName("should return null when email is blank")
    void process_blankEmail_returnsNull() throws Exception {
        EmployeeCsvRow row = buildValidRow();
        row.setEmail("");

        assertThat(processor.process(row)).isNull();
    }

    @Test
    @DisplayName("should return null when email does not contain '@'")
    void process_emailWithoutAtSign_returnsNull() throws Exception {
        EmployeeCsvRow row = buildValidRow();
        row.setEmail("not-an-email-address");

        assertThat(processor.process(row)).isNull();
    }

    @Test
    @DisplayName("should return null when email is null")
    void process_nullEmail_returnsNull() throws Exception {
        EmployeeCsvRow row = buildValidRow();
        row.setEmail(null);

        assertThat(processor.process(row)).isNull();
    }

    // ── department validation tests ───────────────────────────────────────────

    @Test
    @DisplayName("should return null when department is blank")
    void process_blankDepartment_returnsNull() throws Exception {
        EmployeeCsvRow row = buildValidRow();
        row.setDepartment("  ");

        assertThat(processor.process(row)).isNull();
    }

    @Test
    @DisplayName("should return null when department is null")
    void process_nullDepartment_returnsNull() throws Exception {
        EmployeeCsvRow row = buildValidRow();
        row.setDepartment(null);

        assertThat(processor.process(row)).isNull();
    }

    // ── salary validation tests ───────────────────────────────────────────────

    @Test
    @DisplayName("should return null when salary is not a valid number")
    void process_invalidSalary_returnsNull() throws Exception {
        EmployeeCsvRow row = buildValidRow();
        row.setSalary("not-a-number");

        assertThat(processor.process(row)).isNull();
    }

    @Test
    @DisplayName("should return null when salary is zero")
    void process_zeroSalary_returnsNull() throws Exception {
        EmployeeCsvRow row = buildValidRow();
        row.setSalary("0");

        assertThat(processor.process(row)).isNull();
    }

    @Test
    @DisplayName("should return null when salary is negative")
    void process_negativeSalary_returnsNull() throws Exception {
        EmployeeCsvRow row = buildValidRow();
        row.setSalary("-5000.00");

        assertThat(processor.process(row)).isNull();
    }

    @Test
    @DisplayName("should return null when salary is blank")
    void process_blankSalary_returnsNull() throws Exception {
        EmployeeCsvRow row = buildValidRow();
        row.setSalary("");

        assertThat(processor.process(row)).isNull();
    }

    // ── hire_date validation tests ────────────────────────────────────────────

    @Test
    @DisplayName("should return null when hire_date has wrong format")
    void process_invalidDateFormat_returnsNull() throws Exception {
        EmployeeCsvRow row = buildValidRow();
        row.setHireDate("15/03/2021"); // wrong format – expected yyyy-MM-dd

        assertThat(processor.process(row)).isNull();
    }

    @Test
    @DisplayName("should return null when hire_date is not a date at all")
    void process_nonDateHireDate_returnsNull() throws Exception {
        EmployeeCsvRow row = buildValidRow();
        row.setHireDate("not-a-date");

        assertThat(processor.process(row)).isNull();
    }

    @Test
    @DisplayName("should return null when hire_date is blank")
    void process_blankHireDate_returnsNull() throws Exception {
        EmployeeCsvRow row = buildValidRow();
        row.setHireDate("  ");

        assertThat(processor.process(row)).isNull();
    }

    // ── Salary precision test ─────────────────────────────────────────────────

    @Test
    @DisplayName("should parse salary with two decimal places correctly")
    void process_salaryWithDecimals_parsedCorrectly() throws Exception {
        EmployeeCsvRow row = buildValidRow();
        row.setSalary("123456.78");

        Employee result = processor.process(row);

        assertThat(result).isNotNull();
        assertThat(result.getSalary()).isEqualByComparingTo(new BigDecimal("123456.78"));
    }

    // ── Helper ───────────────────────────────────────────────────────────────

    /**
     * Builds a fully valid {@link EmployeeCsvRow} that passes all validation rules.
     * Individual tests override specific fields to trigger their targeted failure case.
     */
    private EmployeeCsvRow buildValidRow() {
        EmployeeCsvRow row = new EmployeeCsvRow();
        row.setFirstName("Alice");
        row.setLastName("Johnson");
        row.setEmail("alice.johnson@example.com");
        row.setDepartment("Engineering");
        row.setSalary("95000.00");
        row.setHireDate("2021-03-15");
        return row;
    }
}
