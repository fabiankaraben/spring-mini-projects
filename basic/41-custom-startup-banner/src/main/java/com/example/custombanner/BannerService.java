package com.example.custombanner;

/**
 * Service interface for providing banner-related messages.
 * This interface defines methods for retrieving messages displayed in the application.
 */
public interface BannerService {

    /**
     * Retrieves the welcome message for the application.
     *
     * @return the banner message as a string
     */
    String getBannerMessage();
}
