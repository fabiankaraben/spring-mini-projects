package com.example.profileconfig.service;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * The Development implementation of MessageService.
 * This class is annotated with @Profile("dev"), meaning Spring will only
 * create an instance of this bean when the "dev" profile is active.
 */
@Service
@Profile("dev")
public class DevMessageService implements MessageService {

    @Override
    public String getMessage() {
        return "You are connected to the DEVELOPMENT module. Experimental features enabled!";
    }
}
