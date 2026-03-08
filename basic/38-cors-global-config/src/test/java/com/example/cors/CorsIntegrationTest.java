package com.example.cors;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(controllers = GreetingController.class)
@Import(WebConfig.class)
public class CorsIntegrationTest {

	@Autowired
	private MockMvc mockMvc;

	@Test
	public void testCorsWithAllowedOrigin() throws Exception {
		mockMvc.perform(get("/greeting")
				.header(HttpHeaders.ORIGIN, "http://localhost:9090"))
				.andDo(print())
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:9090"))
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_CREDENTIALS, "true"));
	}

	@Test
	public void testCorsWithDisallowedOrigin() throws Exception {
		mockMvc.perform(get("/greeting")
				.header(HttpHeaders.ORIGIN, "http://evil.com"))
				.andDo(print())
				.andExpect(status().isForbidden());
	}

	@Test
	public void testPreflightRequest() throws Exception {
		mockMvc.perform(options("/greeting")
				.header(HttpHeaders.ORIGIN, "http://localhost:9090")
				.header(HttpHeaders.ACCESS_CONTROL_REQUEST_METHOD, "GET"))
				.andDo(print())
				.andExpect(status().isOk())
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_ORIGIN, "http://localhost:9090"))
				.andExpect(header().string(HttpHeaders.ACCESS_CONTROL_ALLOW_METHODS, "GET,POST,PUT,DELETE,OPTIONS"));
	}
}
