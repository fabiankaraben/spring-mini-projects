package com.example.profileconfig.service;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * The Production implementation of MessageService.
 * This class is annotated with @Profile("prod"), meaning Spring will only
 * create an instance of this bean when the "prod" profile is active.
 */
@Service
@Profile("prod")
public class ProdMessageService implements MessageService {

    @Override
    public String getMessage() {
        return "You are connected to the PRODUCTION module. System is stable and highly available.";
    }
}
