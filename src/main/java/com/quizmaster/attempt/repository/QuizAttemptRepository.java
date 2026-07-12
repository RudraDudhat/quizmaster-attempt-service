package com.quizmaster.attempt.repository;

import com.quizmaster.attempt.entity.QuizAttempt;
import com.quizmaster.attempt.enums.AttemptStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Attempt persistence.
 *
 * NOTE (microservices): a quiz is referenced by its UUID and a student by
 * their user id — there are no JPA relationships to Quiz/User (they live in
 * other services), so every query filters on {@code quizUuid} /
 * {@code studentUserId} scalar columns, never on a joined entity.
 */
@Repository
public interface QuizAttemptRepository extends JpaRepository<QuizAttempt, Long> {

    Optional<QuizAttempt> findByUuid(UUID uuid);

    Optional<QuizAttempt> findByUuidAndStudentUserId(UUID uuid, Long studentUserId);

    Page<QuizAttempt> findByStudentUserIdOrderByCreatedAtDesc(Long studentUserId, Pageable pageable);

    @Query("SELECT COUNT(a) FROM QuizAttempt a WHERE a.quizUuid = :quizUuid AND a.studentUserId = :studentUserId AND a.status <> 'INVALIDATED'")
    int countValidAttempts(@Param("quizUuid") UUID quizUuid, @Param("studentUserId") Long studentUserId);

    @Query("SELECT a FROM QuizAttempt a WHERE a.quizUuid = :quizUuid AND a.studentUserId = :studentUserId AND a.status = 'IN_PROGRESS'")
    Optional<QuizAttempt> findActiveAttempt(@Param("quizUuid") UUID quizUuid, @Param("studentUserId") Long studentUserId);

    @Query("SELECT MAX(a.submittedAt) FROM QuizAttempt a WHERE a.quizUuid = :quizUuid AND a.studentUserId = :studentUserId AND a.status = 'SUBMITTED'")
    Optional<Instant> findLastSubmittedAt(@Param("quizUuid") UUID quizUuid, @Param("studentUserId") Long studentUserId);

    @Query("SELECT a FROM QuizAttempt a WHERE a.status = 'IN_PROGRESS' AND a.deadlineAt <= :now")
    List<QuizAttempt> findExpiredAttempts(@Param("now") Instant now);

    /** Admin reset: a student's attempts for one quiz, by their denormalized email. */
    List<QuizAttempt> findByStudentEmailAndQuizUuidAndStatusIn(
            String studentEmail, UUID quizUuid, List<AttemptStatus> statuses);

    @Query("SELECT COUNT(a) FROM QuizAttempt a WHERE a.quizUuid = :quizUuid AND a.status IN ('SUBMITTED','AUTO_SUBMITTED') AND a.marksObtained > :marks")
    int countBetterAttempts(@Param("quizUuid") UUID quizUuid, @Param("marks") java.math.BigDecimal marks);

    /**
     * Recompute rank for every submitted attempt of a quiz using a RANK()
     * window so ties share a rank and earlier attempts get pushed down when a
     * higher-scoring attempt is added later. Scoped by quiz_uuid.
     */
    @Modifying
    @Query(value = """
            UPDATE quiz_attempts qa
            SET "rank" = ranked.new_rank
            FROM (
                SELECT id,
                       RANK() OVER (ORDER BY marks_obtained DESC) AS new_rank
                FROM quiz_attempts
                WHERE quiz_uuid = :quizUuid
                  AND status IN ('SUBMITTED', 'AUTO_SUBMITTED')
            ) AS ranked
            WHERE qa.id = ranked.id
            """, nativeQuery = true)
    void recomputeRanksForQuiz(@Param("quizUuid") UUID quizUuid);

    /** Admin queue: attempts with at least one essay still awaiting grading. */
    @Query("""
            SELECT DISTINCT a FROM QuizAttempt a
            JOIN AttemptAnswer aa ON aa.attempt = a
            WHERE a.status IN ('SUBMITTED', 'AUTO_SUBMITTED')
              AND aa.isCorrect IS NULL
              AND aa.isSkipped = false
            ORDER BY a.submittedAt ASC
            """)
    Page<QuizAttempt> findAttemptsPendingReview(Pageable pageable);
}
