package com.example.profileconfig.service;

import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/**
 * The Default implementation of MessageService.
 * We use @Profile("default") to ensure this bean is only created if no
 * active profiles are specified. This acts as a fallback mechanism.
 */
@Service
@Profile("default")
public class DefaultMessageService implements MessageService {

    @Override
    public String getMessage() {
        return "You are connected to the DEFAULT environment module. No specific profile was loaded.";
    }
}
