package com.example.formlogin.controller;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * MVC Controller that maps URL paths to Thymeleaf HTML templates.
 *
 * <p>In a form-login application the controller returns <em>view names</em>
 * (Strings) instead of {@code ResponseEntity} objects. Thymeleaf resolves
 * the view name to a template under {@code src/main/resources/templates/}.
 *
 * <p>Spring Security intercepts requests before they reach this controller:
 * <ul>
 *   <li>Unauthenticated requests to protected URLs are redirected to
 *       {@code /login}.</li>
 *   <li>Authenticated requests with insufficient roles receive HTTP 403.</li>
 *   <li>Only requests that pass all security checks reach the controller
 *       methods below.</li>
 * </ul>
 */
@Controller
public class PageController {

    /**
     * Renders the custom login page.
     *
     * <p>This endpoint is declared as {@code permitAll()} in
     * {@link com.example.formlogin.config.SecurityConfig}, so it is accessible
     * to unauthenticated users. Thymeleaf renders {@code templates/login.html}.
     *
     * <p>Spring Security automatically detects the {@code error} and
     * {@code logout} query parameters and exposes them to the template via
     * the model.
     *
     * @return the logical view name "login"
     */
    @GetMapping("/login")
    public String loginPage() {
        // Returning "login" tells Thymeleaf to render templates/login.html
        return "login";
    }

    /**
     * Renders the main dashboard page (visible to all authenticated users).
     *
     * <p>Spring Security ensures only authenticated users can reach this
     * handler. The {@link Authentication} object is injected by Spring MVC
     * and gives us access to the current user's name and roles.
     *
     * @param authentication the currently authenticated principal
     * @param model          the Thymeleaf model to pass data to the template
     * @return the logical view name "dashboard"
     */
    @GetMapping("/dashboard")
    public String dashboardPage(Authentication authentication, Model model) {
        // Pass the username to the template so it can greet the user
        model.addAttribute("username", authentication.getName());

        // Pass all roles so the template can conditionally show admin links
        model.addAttribute("roles", authentication.getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .toList());

        return "dashboard";
    }

    /**
     * Renders the admin-only panel page.
     *
     * <p>Access is restricted to users with {@code ROLE_ADMIN} by the
     * security configuration ({@code .requestMatchers("/admin/**").hasRole("ADMIN")}).
     * Any user without the ADMIN role who tries to access this URL will
     * receive an HTTP 403 Forbidden response.
     *
     * @param authentication the currently authenticated principal
     * @param model          the Thymeleaf model
     * @return the logical view name "admin"
     */
    @GetMapping("/admin")
    public String adminPage(Authentication authentication, Model model) {
        // Let the template know who the current admin is
        model.addAttribute("username", authentication.getName());
        return "admin";
    }

    /**
     * Renders the user profile page (accessible to all authenticated users).
     *
     * <p>Displays the current user's username and their assigned roles.
     *
     * @param authentication the currently authenticated principal
     * @param model          the Thymeleaf model
     * @return the logical view name "profile"
     */
    @GetMapping("/profile")
    public String profilePage(Authentication authentication, Model model) {
        model.addAttribute("username", authentication.getName());
        model.addAttribute("roles", authentication.getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .toList());
        return "profile";
    }
}
