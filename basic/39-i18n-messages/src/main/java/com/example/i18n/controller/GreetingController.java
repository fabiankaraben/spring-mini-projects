package com.example.i18n.controller;

import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;


@RestController
public class GreetingController {

    private final MessageSource messageSource;

    public GreetingController(MessageSource messageSource) {
        this.messageSource = messageSource;
    }

    @GetMapping("/hello")
    public String hello() {
        // LocaleContextHolder.getLocale() retrieves the locale from the current thread,
        // which is set by the LocaleResolver based on the Accept-Language header.
        return messageSource.getMessage("greeting.hello", null, LocaleContextHolder.getLocale());
    }

    @GetMapping("/welcome")
    public String welcome(@RequestParam(name = "name", defaultValue = "User") String name) {
        Object[] args = new Object[]{name};
        return messageSource.getMessage("greeting.welcome", args, LocaleContextHolder.getLocale());
    }
}
