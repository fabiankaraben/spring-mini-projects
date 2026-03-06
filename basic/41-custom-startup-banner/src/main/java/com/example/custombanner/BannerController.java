package com.example.custombanner;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for handling requests related to the banner application.
 * This controller provides endpoints for interacting with the custom startup banner application.
 */
@RestController
@RequestMapping("/")
public class BannerController {

    private final BannerService bannerService;

    /**
     * Constructor for BannerController.
     * Uses constructor-based dependency injection for the BannerService.
     *
     * @param bannerService the service providing banner messages
     */
    public BannerController(BannerService bannerService) {
        this.bannerService = bannerService;
    }

    /**
     * Handles GET requests to the root path.
     * Returns the welcome message from the banner service.
     *
     * @return the banner message as a string
     */
    @GetMapping
    public String home() {
        return bannerService.getBannerMessage();
    }
}
