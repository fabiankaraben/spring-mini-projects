package com.example.formdata;

import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

/**
 * Controller responsible for handling HTTP form submissions.
 * 
 * @RestController indicates that the data returned by each method will be
 *                 written straight into the response body.
 */
@RestController
public class FormController {

    private final FormHandlerService formHandlerService;

    // Constructor injection for the service dependency
    public FormController(FormHandlerService formHandlerService) {
        this.formHandlerService = formHandlerService;
    }

    /**
     * Handles POST requests containing URL-encoded form data.
     * Note the "consumes" attribute ensures this endpoint only accepts
     * "application/x-www-form-urlencoded" requests.
     * The @ModelAttribute annotation automatically binds the incoming form fields
     * to the UserForm record properties.
     *
     * @param form the mapped form data
     * @return a response with the echoed data
     */
    @PostMapping(path = "/submit", consumes = MediaType.APPLICATION_FORM_URLENCODED_VALUE)
    public Map<String, Object> handleFormSubmission(@ModelAttribute UserForm form) {
        // Delegate the processing to the service layer
        return formHandlerService.processForm(form);
    }
}
