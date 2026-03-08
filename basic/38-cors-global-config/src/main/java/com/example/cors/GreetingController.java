package com.example.cors;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * A simple REST Controller to demonstrate CORS.
 * The endpoints here will be subject to the global CORS configuration defined in WebConfig.
 */
@RestController
public class GreetingController {

	/**
	 * A simple GET endpoint.
	 * Try accessing this from allowed and disallowed origins to test CORS.
	 * 
	 * @return a greeting message
	 */
	@GetMapping("/greeting")
	public String greeting() {
		return "Hello, CORS World!";
	}
}
