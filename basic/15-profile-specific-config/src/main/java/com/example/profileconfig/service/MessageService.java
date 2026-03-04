package com.example.profileconfig.service;

/**
 * Interface defining the shared contract.
 * We will have multiple implementations of this interface, each tied to a
 * specific profile.
 * At runtime, Spring will inject the correct implementation based on the active
 * profile.
 */
public interface MessageService {

    /**
     * Gets a message customized by environment.
     * 
     * @return the profile-specific message
     */
    String getMessage();
}
