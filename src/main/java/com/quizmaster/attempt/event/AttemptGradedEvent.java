package com.quizmaster.attempt.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Consumed from {@code quiz.attempt.graded} — grading-service has finished
 * scoring and this carries the result to persist. Mirror of grading-service's
 * AttemptGradedEvent — keep field names in sync.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttemptGradedEvent {

    private String attemptUuid;
    private String quizUuid;
    private Long studentUserId;
    private String studentEmail;
    private String quizTitle;
    private boolean autoSubmitted;

    private BigDecimal marksObtained;
    private BigDecimal positiveMarks;
    private BigDecimal negativeMarksDeducted;
    private BigDecimal totalMarksPossible;
    private BigDecimal percentage;
    private Boolean isPassed;

    private int correctCount;
    private int wrongCount;
    private int skippedCount;
    private int pendingReviewCount;

    private List<AnswerResult> results;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AnswerResult {
        private Long quizQuestionId;
        private Boolean isCorrect;
        private BigDecimal marksAwarded;
    }
}
