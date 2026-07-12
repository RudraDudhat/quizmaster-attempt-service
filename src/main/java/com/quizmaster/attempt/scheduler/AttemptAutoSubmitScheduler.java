package com.quizmaster.attempt.scheduler;

import com.quizmaster.attempt.entity.QuizAttempt;
import com.quizmaster.attempt.repository.QuizAttemptRepository;
import com.quizmaster.attempt.service.AttemptService;
import jakarta.persistence.OptimisticLockException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Scheduled job that runs every 30 seconds to automatically submit quiz
 * attempts whose deadline has passed while still IN_PROGRESS.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AttemptAutoSubmitScheduler {

    private final QuizAttemptRepository attemptRepository;
    private final AttemptService attemptService;

    @Scheduled(fixedDelay = 30000)
    public void autoSubmitExpiredAttempts() {
        List<QuizAttempt> expired = attemptRepository.findExpiredAttempts(Instant.now());

        if (expired.isEmpty())
            return;

        log.info("Found {} expired attempt(s) to auto-submit", expired.size());

        for (QuizAttempt attempt : expired) {
            try {
                attemptService.autoSubmitAttempt(attempt);
                log.info("Auto-submitted: attempt={}, student={}, score={}/{}",
                        attempt.getId(),
                        attempt.getStudentUserId(),
                        attempt.getMarksObtained(),
                        attempt.getTotalMarksPossible());
            } catch (OptimisticLockingFailureException | OptimisticLockException e) {
                log.info("Skipping attempt {} — concurrently submitted by user", attempt.getId());
            } catch (Exception e) {
                log.error("Failed to auto-submit attempt {}: {}", attempt.getId(), e.getMessage(), e);
            }
        }

        log.info("Auto-submitted {} expired attempts", expired.size());
    }
}
