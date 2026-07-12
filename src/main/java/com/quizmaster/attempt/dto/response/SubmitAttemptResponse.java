package com.quizmaster.attempt.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SubmitAttemptResponse {
    private String attemptUuid;
    private String quizUuid;
    private String quizTitle;
    private BigDecimal marksObtained;
    private BigDecimal totalMarksPossible;
    private BigDecimal percentage;
    private Boolean isPassed;
    private BigDecimal passMarks;
    private Long timeTakenSeconds;
    private Instant submittedAt;
    private Integer attemptNumber;
    private String status;
}
