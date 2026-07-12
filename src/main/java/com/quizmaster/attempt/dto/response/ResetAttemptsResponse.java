package com.quizmaster.attempt.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ResetAttemptsResponse {
    private String studentEmail;
    private String quizUuid;
    private String quizTitle;
    private int resetCount;
    private String message;
}
