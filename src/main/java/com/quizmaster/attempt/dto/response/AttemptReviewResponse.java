package com.quizmaster.attempt.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttemptReviewResponse {
    private String attemptUuid;
    private String quizTitle;
    private List<ReviewQuestionDto> questions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReviewQuestionDto {
        private String questionUuid;
        private String questionText;
        private String questionType;
        private BigDecimal marks;
        private BigDecimal marksAwarded;
        private List<UUID> studentSelectedOptionUuids;
        private List<UUID> correctOptionUuids;
        private String textAnswer;
        private Boolean isCorrect;
        private Boolean isSkipped;
        private Boolean hintUsed;
        private Integer timeSpentSeconds;
        private String explanation;
        private String codeContent;
        private String codeLanguage;
        private String mediaUrl;
        private List<ReviewOptionDto> options;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ReviewOptionDto {
        private UUID uuid;
        private String optionText;
        private String mediaUrl;
        private Integer optionOrder;
        private Boolean isCorrect; // revealed post-submission
    }
}
