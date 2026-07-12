package com.quizmaster.attempt.entity;

import com.quizmaster.attempt.enums.AttemptStatus;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "quiz_attempts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QuizAttempt {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Version
    // Use a SQL default so adding this column to a populated table backfills
    // existing rows to 0 instead of failing with a NOT NULL violation.
    @Column(nullable = false, columnDefinition = "BIGINT NOT NULL DEFAULT 0")
    private Long version;

    @Column(nullable = false, updatable = false)
    private UUID uuid;

    // ─── Cross-service references (NO JPA relationship) ───
    // Quiz lives in quiz-service, User lives in auth-service. We keep only
    // their identifiers here — never a foreign key across a service boundary.

    @Column(name = "quiz_uuid", nullable = false, updatable = false)
    private UUID quizUuid;

    @Column(name = "student_user_id", nullable = false, updatable = false)
    private Long studentUserId;

    // Denormalised at start-time so result/history responses need no remote
    // call back to auth-service just to show who the attempt belongs to.
    @Column(name = "student_email")
    private String studentEmail;

    // ─── Quiz snapshot ────────────────────────────────────
    // The full quiz definition captured once at attempt start. Serving
    // questions and grading read from THIS, not from quiz-service — so an
    // in-flight attempt is immune to mid-attempt quiz edits and even to
    // quiz-service being down.

    @Column(name = "quiz_title")
    private String quizTitle;

    @Column(name = "pass_marks", precision = 8, scale = 2)
    private BigDecimal passMarks;

    @Column(name = "quiz_snapshot_json", columnDefinition = "TEXT")
    private String quizSnapshotJson;

    @Column(name = "attempt_number", nullable = false)
    private Integer attemptNumber;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private AttemptStatus status;

    // --- Timing ---

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "submitted_at")
    private Instant submittedAt;

    // Set when grading-service's result is applied. Null between submit and
    // grading (grading is asynchronous over Kafka).
    @Column(name = "graded_at")
    private Instant gradedAt;

    @Column(name = "deadline_at", nullable = false)
    private Instant deadlineAt;

    // --- Scoring ---

    @Column(name = "total_marks_possible", nullable = false, precision = 8, scale = 2)
    private BigDecimal totalMarksPossible;

    @Column(name = "marks_obtained", nullable = false, precision = 8, scale = 2)
    private BigDecimal marksObtained;

    @Column(name = "positive_marks", nullable = false, precision = 8, scale = 2)
    private BigDecimal positiveMarks;

    @Column(name = "negative_marks_deducted", nullable = false, precision = 8, scale = 2)
    private BigDecimal negativeMarksDeducted;

    @Column(precision = 5, scale = 2)
    private BigDecimal percentage;

    @Column(name = "is_passed")
    private Boolean isPassed;

    @Column(name = "rank")
    private Integer rank;

    // --- Anti-cheat ---

    @Column(name = "tab_switch_count", nullable = false)
    private Integer tabSwitchCount;

    @Column(name = "fullscreen_exit_count", nullable = false)
    private Integer fullscreenExitCount;

    @Column(name = "is_flagged_suspicious", nullable = false)
    private Boolean isFlaggedSuspicious;

    @Column(name = "invalidation_reason", columnDefinition = "TEXT")
    private String invalidationReason;

    @Column(name = "question_order", columnDefinition = "TEXT")
    private String questionOrder;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "user_agent", columnDefinition = "TEXT")
    private String userAgent;

    // --- Timestamps ---

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        if (uuid == null)
            uuid = UUID.randomUUID();
        if (attemptNumber == null)
            attemptNumber = 1;
        if (status == null)
            status = AttemptStatus.IN_PROGRESS;
        if (startedAt == null)
            startedAt = Instant.now();
        if (totalMarksPossible == null)
            totalMarksPossible = BigDecimal.ZERO;
        if (marksObtained == null)
            marksObtained = BigDecimal.ZERO;
        if (positiveMarks == null)
            positiveMarks = BigDecimal.ZERO;
        if (negativeMarksDeducted == null)
            negativeMarksDeducted = BigDecimal.ZERO;
        if (tabSwitchCount == null)
            tabSwitchCount = 0;
        if (fullscreenExitCount == null)
            fullscreenExitCount = 0;
        if (isFlaggedSuspicious == null)
            isFlaggedSuspicious = false;
        if (questionOrder == null)
            questionOrder = "";
        if (version == null)
            version = 0L;
    }
}
