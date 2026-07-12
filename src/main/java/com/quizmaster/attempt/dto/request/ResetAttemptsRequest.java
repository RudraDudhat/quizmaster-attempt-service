package com.quizmaster.attempt.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Admin request to reset (invalidate) a student's attempts for a quiz.
 * Keyed by the student's email — attempt-service stores that denormalized on
 * each attempt, so no user-id lookup against auth-service is needed.
 */
@Data
public class ResetAttemptsRequest {

    @NotBlank(message = "studentEmail is required")
    private String studentEmail;

    @NotBlank(message = "quizUuid is required")
    private String quizUuid;
}
