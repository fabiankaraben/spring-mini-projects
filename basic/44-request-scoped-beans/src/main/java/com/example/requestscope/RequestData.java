package com.example.requestscope;

import org.springframework.stereotype.Component;
import org.springframework.web.context.annotation.RequestScope;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * This bean is created once per HTTP request.
 * It holds state that is specific to the current request processing.
 */
@Component
@RequestScope
public class RequestData {

    private final String requestId = UUID.randomUUID().toString();
    private final List<String> logs = new ArrayList<>();
    private String clientInfo;

    public void addLog(String message) {
        logs.add(message);
    }

    public String getRequestId() {
        return requestId;
    }

    public List<String> getLogs() {
        return logs;
    }

    public String getClientInfo() {
        return clientInfo;
    }

    public void setClientInfo(String clientInfo) {
        this.clientInfo = clientInfo;
    }
}
