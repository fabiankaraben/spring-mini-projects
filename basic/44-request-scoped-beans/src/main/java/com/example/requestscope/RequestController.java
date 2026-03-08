package com.example.requestscope;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
public class RequestController {

    private final RequestProcessingService service;
    private final RequestData requestData;

    public RequestController(RequestProcessingService service, RequestData requestData) {
        this.service = service;
        this.requestData = requestData;
    }

    @GetMapping("/api/process")
    public Map<String, Object> process(@RequestParam(defaultValue = "test") String input) {
        requestData.addLog("Controller received request with input: " + input);
        requestData.setClientInfo("Client-User-Agent-Placeholder"); 

        // Call service which also uses the SAME RequestData instance
        service.processData(input);

        requestData.addLog("Controller preparing response");

        return Map.of(
            "requestId", requestData.getRequestId(),
            "clientInfo", requestData.getClientInfo(),
            "processingLogs", requestData.getLogs()
        );
    }
}
