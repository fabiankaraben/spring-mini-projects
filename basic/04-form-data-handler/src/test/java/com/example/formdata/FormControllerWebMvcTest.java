package com.example.formdata;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
// Note: @MockBean is deprecated since Spring Boot 3.4.0. We use @MockitoBean.
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import java.util.Map;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

/**
 * Sliced integration test for FormController.
 * 
 * @WebMvcTest loads only the web layer (controllers) rather than the entire
 *             application context.
 */
@WebMvcTest(FormController.class)
class FormControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    // Mocking the dependencies within the Spring context
    @MockitoBean
    private FormHandlerService formHandlerService;

    @Test
    void testHandleFormSubmissionViaHttp() throws Exception {
        // Mocking the behavior of the service layer
        UserForm mockedForm = new UserForm("alice", "alice@test.com", "Hi!");
        Map<String, Object> expectedMap = Map.of(
                "status", "success",
                "message", "Form data successfully captured.",
                "data", mockedForm);

        when(formHandlerService.processForm(any(UserForm.class))).thenReturn(expectedMap);

        // Performing the POST request using application/x-www-form-urlencoded
        mockMvc.perform(post("/submit")
                .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                .param("username", "alice")
                .param("email", "alice@test.com")
                .param("message", "Hi!"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("success"))
                .andExpect(jsonPath("$.message").value("Form data successfully captured."))
                .andExpect(jsonPath("$.data.username").value("alice"))
                .andExpect(jsonPath("$.data.email").value("alice@test.com"));
    }
}
