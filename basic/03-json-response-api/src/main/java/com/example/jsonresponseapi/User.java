package com.example.jsonresponseapi;

/**
 * Represents a User in the system.
 * This is a plain old Java object (POJO) that will be mapped and serialized
 * into JSON
 * by Jackson, which is included in the Spring Web starter.
 */
public class User {

    private Long id;
    private String name;
    private String email;
    private String role;

    /**
     * Default constructor required by some serialization frameworks like Jackson.
     */
    public User() {
    }

    /**
     * Parameterized constructor to initialize a new User.
     *
     * @param id    The unique identifier for the user.
     * @param name  The full name of the user.
     * @param email The contact email of the user.
     * @param role  The assigned role of the user.
     */
    public User(Long id, String name, String email, String role) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.role = role;
    }

    // Getters and Setters are required for JSON serialization.
    // Jackson uses these to access and set private fields during conversion.

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

    public String getRole() {
        return role;
    }

    public void setRole(String role) {
        this.role = role;
    }
}
