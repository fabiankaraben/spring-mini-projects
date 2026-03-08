package com.example.requestscope;

import org.springframework.stereotype.Service;

@Service
public class RequestProcessingService {

    private final RequestData requestData;

    public RequestProcessingService(RequestData requestData) {
        this.requestData = requestData;
    }

    public void processData(String input) {
        // We can access the request-scoped bean here without passing it as a parameter
        requestData.addLog("Service processing input: " + input);
        
        // Simulate some logic
        String processed = input.toUpperCase();
        requestData.addLog("Service finished processing: " + processed);
    }
}
