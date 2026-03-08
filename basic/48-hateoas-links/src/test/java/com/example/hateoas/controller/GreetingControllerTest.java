package com.example.hateoas.controller;

import com.example.hateoas.domain.Greeting;
import com.example.hateoas.service.GreetingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.hateoas.MediaTypes;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.is;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * Sliced Integration Test for GreetingController using @WebMvcTest.
 */
@WebMvcTest(GreetingController.class)
public class GreetingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private GreetingService greetingService;

    @Test
    public void greetingShouldReturnDefaultMessageWithLinks() throws Exception {
        given(greetingService.greet("World")).willReturn(new Greeting("Hello, World!"));

        mockMvc.perform(get("/greeting").accept(MediaTypes.HAL_JSON_VALUE))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaTypes.HAL_JSON_VALUE))
                .andExpect(jsonPath("$.content", is("Hello, World!")))
                .andExpect(jsonPath("$._links.self.href").exists())
                .andExpect(jsonPath("$._links.greet-spring.href").exists());
    }

    @Test
    public void greetingShouldReturnCustomMessageWithLinks() throws Exception {
        given(greetingService.greet("Fabian")).willReturn(new Greeting("Hello, Fabian!"));

        mockMvc.perform(get("/greeting").param("name", "Fabian").accept(MediaTypes.HAL_JSON_VALUE))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaTypes.HAL_JSON_VALUE))
                .andExpect(jsonPath("$.content", is("Hello, Fabian!")))
                .andExpect(jsonPath("$._links.self.href").exists())
                .andExpect(jsonPath("$._links.greet-spring.href").exists());
    }

    @Test
    public void greetingShouldNotReturnGreetSpringLinkWhenNameIsSpring() throws Exception {
        given(greetingService.greet("Spring")).willReturn(new Greeting("Hello, Spring!"));

        mockMvc.perform(get("/greeting").param("name", "Spring").accept(MediaTypes.HAL_JSON_VALUE))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.CONTENT_TYPE, MediaTypes.HAL_JSON_VALUE))
                .andExpect(jsonPath("$.content", is("Hello, Spring!")))
                .andExpect(jsonPath("$._links.self.href").exists())
                .andExpect(jsonPath("$._links.greet-spring").doesNotExist());
    }
}
