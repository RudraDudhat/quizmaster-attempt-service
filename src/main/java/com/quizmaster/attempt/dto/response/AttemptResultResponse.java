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
public class AttemptResultResponse {
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
    // Extended fields
    private Integer rank;
    private BigDecimal positiveMarks;
    private BigDecimal negativeMarksDeducted;
    private Integer correctCount;
    private Integer wrongCount;
    private Integer skippedCount;
    /** Number of essay answers awaiting an admin's manual grading. */
    private Integer pendingReviewCount;
}
