package com.example.formdata;

/**
 * A simple record representing the form data submitted by the user.
 * Using a record is a concise way to define immutable data carriers in Java.
 */
public record UserForm(String username, String email, String message) {
}
