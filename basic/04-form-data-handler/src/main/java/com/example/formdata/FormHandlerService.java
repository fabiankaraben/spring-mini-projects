package com.example.formdata;

import org.springframework.stereotype.Service;
import java.util.Map;

/**
 * Service class that handles business logic for form data.
 * Annotated with @Service so Spring will automatically detect and manage it.
 */
@Service
public class FormHandlerService {

    /**
     * Processes the received form data.
     * In a real application, this could save the data to a database.
     * Here, it simply formats an echo response.
     *
     * @param form the parsed form data
     * @return a map echoing the received values
     */
    public Map<String, Object> processForm(UserForm form) {
        return Map.of(
                "status", "success",
                "message", "Form data successfully captured.",
                "data", form);
    }
}
