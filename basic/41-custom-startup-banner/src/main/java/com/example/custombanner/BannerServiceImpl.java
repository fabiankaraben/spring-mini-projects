package com.example.custombanner;

import org.springframework.stereotype.Service;

/**
 * Implementation of BannerService.
 * This service provides the welcome message for the application.
 */
@Service
public class BannerServiceImpl implements BannerService {

    /**
     * Returns the predefined welcome message.
     *
     * @return the banner message
     */
    @Override
    public String getBannerMessage() {
        return "Welcome to the Custom Startup Banner Application!";
    }
}
