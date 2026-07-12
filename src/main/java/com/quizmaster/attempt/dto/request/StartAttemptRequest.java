package com.quizmaster.attempt.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StartAttemptRequest {

    // Which quiz to start. Carried in the body (not the path) so the gateway
    // can route the whole attempt API under a single /api/v1/student/attempts
    // prefix without colliding with quiz-service's /student/quizzes catalog.
    @NotBlank(message = "quizUuid is required")
    private String quizUuid;

    // Optional — only needed if the quiz has an access code
    private String accessCode;
}
