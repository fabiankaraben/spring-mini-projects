package com.example.methodlevelsecurity.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * DTO for create and update document requests.
 *
 * <p>Sent by the client in the request body of {@code POST /api/documents}
 * (create) and {@code PUT /api/documents/{id}} (update).</p>
 */
public class DocumentRequest {

    /** Document title. Must be 1–200 non-blank characters. */
    @NotBlank(message = "Title must not be blank")
    @Size(max = 200, message = "Title must not exceed 200 characters")
    private String title;

    /** Document body text. Must not be blank. */
    @NotBlank(message = "Content must not be blank")
    private String content;

    /**
     * Visibility: {@code "PUBLIC"} or {@code "PRIVATE"}.
     * Defaults to {@code "PRIVATE"} at the service level if absent or invalid.
     */
    private String visibility;

    // ── Getters and setters ───────────────────────────────────────────────────

    public String getTitle() { return title; }

    public void setTitle(String title) { this.title = title; }

    public String getContent() { return content; }

    public void setContent(String content) { this.content = content; }

    public String getVisibility() { return visibility; }

    public void setVisibility(String visibility) { this.visibility = visibility; }
}
