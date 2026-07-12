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
public class AttemptHistoryResponse {
    private String attemptUuid;
    private String quizUuid;
    private String quizTitle;
    private Integer attemptNumber;
    private String status;
    private BigDecimal percentage;
    private Boolean isPassed;
    private BigDecimal marksObtained;
    private BigDecimal totalMarksPossible;
    private Instant startedAt;
    private Instant submittedAt;
    private Long timeTakenSeconds;
    /** True when this attempt has at least one essay still awaiting an
     *  admin's manual grading. Lists should display a "Pending review"
     *  badge instead of pass/fail when this is true. */
    private Boolean hasPendingReview;
}
