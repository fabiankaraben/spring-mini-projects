package com.example.twiliosmssender.controller;

import com.example.twiliosmssender.domain.SmsStatus;
import com.example.twiliosmssender.dto.SendSmsRequest;
import com.example.twiliosmssender.dto.SmsResponse;
import com.example.twiliosmssender.service.SmsService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST controller that exposes the Twilio SMS Sender API endpoints.
 *
 * <p>Base path: {@code /api/sms}
 *
 * <p>Endpoints:
 * <ul>
 *   <li>{@code POST  /api/sms/send}               – Send an SMS via Twilio</li>
 *   <li>{@code GET   /api/sms}                     – List all message records</li>
 *   <li>{@code GET   /api/sms/{id}}                – Get a message by local DB ID</li>
 *   <li>{@code GET   /api/sms/twilio/{sid}}         – Get a message by Twilio SID</li>
 *   <li>{@code GET   /api/sms/status/{status}}      – Get messages by delivery status</li>
 * </ul>
 *
 * <p>The controller is intentionally thin: all business logic and Twilio API calls
 * live in {@link SmsService}. This class only maps HTTP semantics
 * (status codes, request/response bodies) to service method calls.
 */
@RestController
@RequestMapping("/api/sms")
public class SmsController {

    /** The service containing all Twilio and database logic. */
    private final SmsService smsService;

    /**
     * Constructor injection — preferred over field injection for testability.
     *
     * @param smsService the service handling Twilio and persistence operations
     */
    public SmsController(SmsService smsService) {
        this.smsService = smsService;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Send SMS
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Sends an SMS message via Twilio and persists a local audit record.
     *
     * <p>The {@code @Valid} annotation triggers Bean Validation on the request body.
     * If validation fails, Spring returns HTTP 400 before reaching the service layer.
     *
     * <p>Example with curl:
     * <pre>
     * curl -X POST http://localhost:8080/api/sms/send \
     *      -H "Content-Type: application/json" \
     *      -d '{"to": "+15551234567", "body": "Hello from Spring Boot + Twilio!"}'
     * </pre>
     *
     * @param request the validated request body with recipient number and message text
     * @return HTTP 201 Created with the {@link SmsResponse} JSON body
     */
    @PostMapping("/send")
    public ResponseEntity<SmsResponse> sendSms(@Valid @RequestBody SendSmsRequest request) {
        SmsResponse response = smsService.sendSms(request);
        // 201 Created is the conventional status code for a successfully created resource
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // List all messages
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns all SMS message records from the local database.
     *
     * <p>This endpoint reads from the local audit table only — it does NOT call the Twilio API.
     *
     * <p>Example with curl:
     * <pre>
     * curl http://localhost:8080/api/sms
     * </pre>
     *
     * @return HTTP 200 with a JSON array of all {@link SmsResponse} records
     */
    @GetMapping
    public ResponseEntity<List<SmsResponse>> listMessages() {
        return ResponseEntity.ok(smsService.listMessages());
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Get by local database ID
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Retrieves a single SMS message record by its local database ID.
     *
     * <p>Example with curl:
     * <pre>
     * curl http://localhost:8080/api/sms/1
     * </pre>
     *
     * @param id the local PostgreSQL primary key of the SMS record
     * @return HTTP 200 with a {@link SmsResponse} body, or HTTP 404 if not found
     */
    @GetMapping("/{id}")
    public ResponseEntity<SmsResponse> getMessageById(@PathVariable Long id) {
        return ResponseEntity.ok(smsService.getMessageById(id));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Get by Twilio Message SID
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Retrieves a single SMS message record by its Twilio Message SID.
     *
     * <p>The Twilio SID is the unique identifier returned in the API response when
     * an SMS is created. Format: "SM" followed by 32 hex characters.
     *
     * <p>Example with curl:
     * <pre>
     * curl http://localhost:8080/api/sms/twilio/SMxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx
     * </pre>
     *
     * @param sid the Twilio-assigned message SID
     * @return HTTP 200 with a {@link SmsResponse} body, or HTTP 404 if not found
     */
    @GetMapping("/twilio/{sid}")
    public ResponseEntity<SmsResponse> getMessageByTwilioSid(@PathVariable String sid) {
        return ResponseEntity.ok(smsService.getMessageByTwilioSid(sid));
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Get by delivery status
    // ─────────────────────────────────────────────────────────────────────────

    /**
     * Returns all SMS message records with the specified delivery status.
     *
     * <p>Valid status values (case-insensitive): {@code QUEUED}, {@code SENDING},
     * {@code SENT}, {@code DELIVERED}, {@code FAILED}, {@code UNDELIVERED}.
     *
     * <p>Example with curl:
     * <pre>
     * curl http://localhost:8080/api/sms/status/FAILED
     * curl http://localhost:8080/api/sms/status/DELIVERED
     * </pre>
     *
     * @param status the delivery status to filter by (mapped from URL path segment)
     * @return HTTP 200 with a JSON array of matching {@link SmsResponse} records
     */
    @GetMapping("/status/{status}")
    public ResponseEntity<List<SmsResponse>> getMessagesByStatus(@PathVariable SmsStatus status) {
        return ResponseEntity.ok(smsService.getMessagesByStatus(status));
    }
}
