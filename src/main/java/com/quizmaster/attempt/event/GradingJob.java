package com.quizmaster.attempt.event;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Everything grading-service needs to score one attempt. Published as the body
 * of {@code quiz.attempt.submitted}, and also returned by the internal
 * grading-job endpoint for the admin essay-regrade flow.
 *
 * Mirror of grading-service's GradingJob — keep field names in sync.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class GradingJob {

    private String attemptUuid;
    private String quizUuid;
    private Long studentUserId;
    private String studentEmail;
    private String quizTitle;
    private BigDecimal passMarks;
    private BigDecimal totalMarksPossible;
    private boolean autoSubmitted;

    private List<JobQuestion> questions;
    private List<JobAnswer> answers;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JobQuestion {
        private Long quizQuestionId;
        private String questionUuid;
        private String questionType;
        private BigDecimal marks;
        private BigDecimal negativeMarks;
        private BigDecimal hintMarkDeduction;
        private List<JobOption> options;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JobOption {
        private String uuid;
        private String optionText;
        private Integer optionOrder;
        private Boolean isCorrect;
        private String matchPairKey;
        private String matchPairVal;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class JobAnswer {
        private Long quizQuestionId;
        private String questionUuid;
        private String questionType;
        private String selectedOptionIds;
        private String textAnswer;
        private String orderedOptionIds;
        private String matchPairs;
        private Boolean booleanAnswer;
        private Boolean hintUsed;
        private Boolean isSkipped;
        private BigDecimal marksAwarded;
        private Boolean isCorrect;
    }
}
