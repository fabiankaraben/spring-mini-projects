package com.example.i18n.controller;

import com.example.i18n.config.LocaleConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Locale;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GreetingController.class)
@Import(LocaleConfig.class)
public class GreetingControllerIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    void shouldReturnDefaultMessage() throws Exception {
        mockMvc.perform(get("/hello"))
                .andExpect(status().isOk())
                .andExpect(content().string("Hello World!"));
    }

    @Test
    void shouldReturnSpanishMessage() throws Exception {
        mockMvc.perform(get("/hello").header("Accept-Language", "es"))
                .andExpect(status().isOk())
                .andExpect(content().string("¡Hola Mundo!"));
    }

    @Test
    void shouldReturnFrenchMessage() throws Exception {
        mockMvc.perform(get("/hello").locale(Locale.FRENCH))
                .andExpect(status().isOk())
                .andExpect(content().string("Bonjour le monde!"));
    }

    @Test
    void shouldReturnDefaultWelcomeMessage() throws Exception {
        mockMvc.perform(get("/welcome").param("name", "Fabian"))
                .andExpect(status().isOk())
                .andExpect(content().string("Welcome to our application, Fabian!"));
    }

    @Test
    void shouldReturnSpanishWelcomeMessage() throws Exception {
        mockMvc.perform(get("/welcome").param("name", "Fabian").header("Accept-Language", "es"))
                .andExpect(status().isOk())
                .andExpect(content().string("¡Bienvenido a nuestra aplicación, Fabian!"));
    }
}
