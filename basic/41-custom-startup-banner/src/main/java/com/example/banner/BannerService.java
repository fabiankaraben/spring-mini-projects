package com.example.banner;

import org.springframework.stereotype.Service;

/**
 * Service layer for the Banner application.
 * <p>
 * This class encapsulates the business logic, providing the welcome message.
 * Separating logic into services allows for better testing and code organization.
 * </p>
 */
@Service
public class BannerService {

    /**
     * Retrieves the welcome message.
     * 
     * @return The welcome message string.
     */
    public String getWelcomeMessage() {
        return "Application started successfully! Check the console for the custom banner.";
    }
}
