package com.quizmaster.attempt.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StartAttemptResponse {

    private String attemptUuid;
    private String quizUuid;
    private String quizTitle;
    private Instant deadlineAt;
    private Integer timeLimitSeconds;
    private List<AttemptQuestionDto> questions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class AttemptQuestionDto {
        private UUID quizQuestionUuid;
        private String questionUuid;
        private String questionText;
        private String questionType;
        private String difficulty;
        private BigDecimal marks;
        private BigDecimal negativeMarks;
        private Integer perQuestionSecs;
        private Integer displayOrder;
        private String hintText;
        private String mediaUrl;
        private String codeContent;
        private String codeLanguage;
        private List<OptionDto> options;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OptionDto {
        private UUID uuid;
        private String optionText;
        private String mediaUrl;
        private Integer optionOrder;
        // NEVER include isCorrect here
    }
}
