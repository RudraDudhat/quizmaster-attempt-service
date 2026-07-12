package com.quizmaster.attempt.client.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Immutable snapshot of a quiz definition, fetched once from quiz-service's
 * internal API at attempt start and persisted (as JSON) on the attempt.
 *
 * This is the consumer-side mirror of quiz-service's QuizSnapshotResponse —
 * field names MUST stay in sync so Jackson round-trips cleanly. It is also
 * the object we serialise into {@code QuizAttempt.quizSnapshotJson} and read
 * back for every serve/grade operation, so an in-flight attempt is immune to
 * later quiz edits and to quiz-service being unavailable.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuizSnapshot {

    private UUID quizUuid;
    private String title;
    private String status;            // QuizStatus name — e.g. PUBLISHED

    private Instant startsAt;
    private Instant expiresAt;

    private String timerMode;
    private Integer timeLimitSeconds;
    private Integer gracePeriodSeconds;
    private Integer perQuestionSeconds;

    private Integer maxAttempts;
    private Integer cooldownHours;

    private BigDecimal totalMarks;
    private BigDecimal passMarks;
    private BigDecimal negativeMarkingFactor;

    private String accessCode;

    private Integer questionsToServe;
    private Boolean shuffleQuestions;
    private Boolean shuffleOptions;
    private Boolean showCorrectAnswers;

    private List<SnapshotQuestion> questions;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SnapshotQuestion {
        private Long quizQuestionId;      // quiz_questions.id — used in questionOrder + answer linkage
        private UUID quizQuestionUuid;    // quiz_questions.uuid — used by saveAnswer lookup
        private UUID questionUuid;
        private String questionType;      // QuestionType name
        private String questionText;
        private String difficulty;
        private BigDecimal marks;
        private BigDecimal negativeMarks;
        private Integer perQuestionSecs;
        private Integer displayOrder;
        private String hintText;
        private BigDecimal hintMarkDeduction;
        private String mediaUrl;
        private String codeContent;
        private String codeLanguage;
        private String explanation;
        private List<SnapshotOption> options;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class SnapshotOption {
        private UUID uuid;
        private String optionText;
        private Integer optionOrder;
        private Boolean isCorrect;        // present on the internal contract; never leaked to students
        private String mediaUrl;
        private String matchPairKey;
        private String matchPairVal;
    }
}
