package com.example.requestscope;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(RequestController.class)
class RequestControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private RequestProcessingService service;

    // We need to mock the RequestData because it's a request-scoped bean
    // In a @WebMvcTest, the request scope is available, but since we're using it in the controller
    // and potentially filters (though filters are not automatically scanned in WebMvcTest unless configured, 
    // but RequestController depends on it), we should treat it carefully.
    // However, since RequestData is a @Component @RequestScope, it is a bean.
    // But @WebMvcTest only scans @Controller, @ControllerAdvice, etc.
    // It does NOT scan @Component by default.
    // So we need to import it or mock it. 
    // If we mock it, we can verify interactions.
    @MockitoBean
    private RequestData requestData;

    @Test
    void process_ShouldReturnDetails() throws Exception {
        // Given
        String input = "hello";
        given(requestData.getRequestId()).willReturn("mock-id");
        given(requestData.getClientInfo()).willReturn("Client-User-Agent-Placeholder");

        // When
        mockMvc.perform(get("/api/process").param("input", input))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestId").value("mock-id"))
                .andExpect(jsonPath("$.clientInfo").value("Client-User-Agent-Placeholder"));

        // Then
        verify(requestData).addLog(contains("Controller received request"));
        verify(service).processData(input);
    }
}
