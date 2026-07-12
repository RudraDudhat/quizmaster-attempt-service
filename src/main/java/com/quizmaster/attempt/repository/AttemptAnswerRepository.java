package com.quizmaster.attempt.repository;

import com.quizmaster.attempt.entity.AttemptAnswer;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface AttemptAnswerRepository extends JpaRepository<AttemptAnswer, Long> {

    Optional<AttemptAnswer> findByAttemptIdAndQuizQuestionId(Long attemptId, Long quizQuestionId);

    List<AttemptAnswer> findByAttemptId(Long attemptId);

    /**
     * Essay answers awaiting manual grading inside a single attempt.
     *
     * After scoring, auto-graded answers always get a non-null isCorrect;
     * only essays are left as isCorrect IS NULL (and not skipped) pending an
     * admin's review — so this needs no reference to the question type, which
     * now lives in the snapshot rather than a joined entity.
     */
    @Query("""
            SELECT COUNT(a) FROM AttemptAnswer a
            WHERE a.attempt.id = :attemptId
              AND a.isCorrect IS NULL
              AND a.isSkipped = false
            """)
    int countPendingReviewByAttemptId(@Param("attemptId") Long attemptId);
}
