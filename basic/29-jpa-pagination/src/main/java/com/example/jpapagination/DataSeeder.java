package com.example.jpapagination;

import com.example.jpapagination.model.Employee;
import com.example.jpapagination.repository.EmployeeRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Profile;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Seeder to produce 50 dummy records on application startup.
 * This will assist in testing JPA pagination mechanisms.
 */
@Configuration
@Profile("!test")
public class DataSeeder {

    @Bean
    public CommandLineRunner loadData(EmployeeRepository repository) {
        return args -> {
            // Only seed if emptiness allows
            if (repository.count() == 0) {
                String[] departments = { "IT", "HR", "Sales", "Finance", "Marketing" };
                Random random = new Random();
                List<Employee> mockData = new ArrayList<>();

                for (int i = 1; i <= 50; i++) {
                    String dept = departments[random.nextInt(departments.length)];
                    Double salary = 40000.0 + (random.nextDouble() * 60000.0);
                    mockData.add(new Employee("Employee " + i, dept, salary));
                }

                // Batch save the employees
                repository.saveAll(mockData);
                System.out.println("Data Seeder: 50 dummy employees have been created.");
            }
        };
    }
}
