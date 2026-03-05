package com.example.flywaymigrations.entity;

import jakarta.persistence.*;

/**
 * Employee entity representing the 'employees' table.
 * 
 * Notice that we don't need any complex JPA properties to auto-generate this
 * table
 * because Flyway will handle the schema creation via migration scripts.
 * The entity maps to the database structure created by Flyway.
 */
@Entity
@Table(name = "employees")
public class Employee {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 100)
    private String name;

    @Column(nullable = false, unique = true, length = 100)
    private String email;

    // This column was added in the V3 Flyway migration script
    @Column(nullable = false, length = 50)
    private String department;

    public Employee() {
    }

    public Employee(String name, String email, String department) {
        this.name = name;
        this.email = email;
        this.department = department;
    }

    // Getters and Setters

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getDepartment() {
        return department;
    }

    public void setDepartment(String department) {
        this.department = department;
    }
}
