package com.example.actuator_info_endpoint.controller;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.servlet.view.RedirectView;

/**
 * Simple routing controller to provide a starting point for users who access
 * the root path.
 * It automatically redirects root web traffic to the Spring Actuator Info
 * endpoint.
 */
@Controller
public class HomeController {

    /**
     * Redirects the root path ("/") over to "/actuator/info".
     *
     * @return The RedirectView pointing to the info endpoint path.
     */
    @GetMapping("/")
    public RedirectView redirectToInfo() {
        // Perform an HTTP 302 redirect by default
        return new RedirectView("/actuator/info");
    }
}
