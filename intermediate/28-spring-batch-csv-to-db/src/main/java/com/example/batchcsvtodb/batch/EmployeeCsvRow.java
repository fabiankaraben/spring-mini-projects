package com.example.batchcsvtodb.batch;

/**
 * Plain data-transfer object (DTO) that holds one raw row read from the CSV file.
 *
 * <p>The {@code FlatFileItemReader} maps each line of the CSV to an instance of this
 * class using a {@code BeanWrapperFieldSetMapper}. All fields are {@code String}
 * because the reader does not perform any type conversion – that is the responsibility
 * of {@code EmployeeItemProcessor}.
 *
 * <p>CSV column order (must match the {@code names} configured on the reader):
 * <pre>
 *   first_name, last_name, email, department, salary, hire_date
 * </pre>
 */
public class EmployeeCsvRow {

    /** Raw value from the {@code first_name} CSV column. */
    private String firstName;

    /** Raw value from the {@code last_name} CSV column. */
    private String lastName;

    /** Raw value from the {@code email} CSV column. */
    private String email;

    /** Raw value from the {@code department} CSV column. */
    private String department;

    /** Raw value from the {@code salary} CSV column (e.g. "75000.00"). */
    private String salary;

    /** Raw value from the {@code hire_date} CSV column (e.g. "2021-03-15"). */
    private String hireDate;

    // ── No-arg constructor required by BeanWrapperFieldSetMapper ─────────────

    public EmployeeCsvRow() {}

    // ── Getters & setters ─────────────────────────────────────────────────────

    public String getFirstName() { return firstName; }
    public void setFirstName(String firstName) { this.firstName = firstName; }

    public String getLastName() { return lastName; }
    public void setLastName(String lastName) { this.lastName = lastName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getDepartment() { return department; }
    public void setDepartment(String department) { this.department = department; }

    public String getSalary() { return salary; }
    public void setSalary(String salary) { this.salary = salary; }

    public String getHireDate() { return hireDate; }
    public void setHireDate(String hireDate) { this.hireDate = hireDate; }

    @Override
    public String toString() {
        return "EmployeeCsvRow{" +
               "firstName='" + firstName + '\'' +
               ", lastName='" + lastName + '\'' +
               ", email='" + email + '\'' +
               ", department='" + department + '\'' +
               ", salary='" + salary + '\'' +
               ", hireDate='" + hireDate + '\'' + '}';
    }
}
