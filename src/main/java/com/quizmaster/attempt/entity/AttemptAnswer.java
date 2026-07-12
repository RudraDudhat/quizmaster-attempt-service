package com.quizmaster.attempt.entity;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "attempt_answers", uniqueConstraints = {
        @UniqueConstraint(columnNames = { "attempt_id", "quiz_question_id" })
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AttemptAnswer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "attempt_id", nullable = false)
    private QuizAttempt attempt;

    // Identifiers only — the QuizQuestion / Question rows live in quiz-service.
    // quizQuestionId is the quiz_questions.id from the snapshot (also used in
    // the attempt's questionOrder CSV); questionUuid + questionType let us
    // grade and render without ever calling quiz-service.

    @Column(name = "quiz_question_id", nullable = false)
    private Long quizQuestionId;

    @Column(name = "question_uuid", nullable = false)
    private java.util.UUID questionUuid;

    @Column(name = "question_type", length = 40)
    private String questionType;

    // --- Student's response ---

    @Column(name = "selected_option_ids", columnDefinition = "TEXT")
    private String selectedOptionIds;

    @Column(name = "text_answer", columnDefinition = "TEXT")
    private String textAnswer;

    @Column(name = "ordered_option_ids", columnDefinition = "TEXT")
    private String orderedOptionIds;

    @Column(name = "match_pairs", columnDefinition = "TEXT")
    private String matchPairs;

    @Column(name = "boolean_answer")
    private Boolean booleanAnswer;

    // --- Hint ---

    @Column(name = "hint_used", nullable = false)
    private Boolean hintUsed;

    // --- Grading ---

    @Column(name = "is_correct")
    private Boolean isCorrect;

    @Column(name = "is_skipped", nullable = false)
    private Boolean isSkipped;

    @Column(name = "marks_awarded", nullable = false, precision = 6, scale = 2)
    private BigDecimal marksAwarded;

    @Column(name = "is_flagged", nullable = false)
    private Boolean isFlagged;

    @Column(name = "manual_grade_note", columnDefinition = "TEXT")
    private String manualGradeNote;

    @Column(name = "graded_by_user_id")
    private Long gradedByUserId;

    @Column(name = "graded_at")
    private Instant gradedAt;

    // --- Timing ---

    @Column(name = "time_spent_seconds", nullable = false)
    private Integer timeSpentSeconds;

    @Column(name = "answered_at")
    private Instant answeredAt;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        if (hintUsed == null)
            hintUsed = false;
        if (isSkipped == null)
            isSkipped = false;
        if (marksAwarded == null)
            marksAwarded = BigDecimal.ZERO;
        if (isFlagged == null)
            isFlagged = false;
        if (timeSpentSeconds == null)
            timeSpentSeconds = 0;
    }
}
