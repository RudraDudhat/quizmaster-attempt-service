package com.quizmaster.attempt.messaging;

import com.quizmaster.attempt.event.AttemptGradedEvent;
import com.quizmaster.attempt.service.AttemptService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes {@code quiz.attempt.graded} and persists the score grading-service
 * computed. attempt-service does no scoring itself — it is the store of record
 * that applies results off this event.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AttemptGradedListener {

    private final AttemptService attemptService;

    @KafkaListener(topics = KafkaTopics.ATTEMPT_GRADED, groupId = "attempt-service")
    public void onAttemptGraded(AttemptGradedEvent event) {
        log.info("Applying grade for attempt={} marks={}/{}",
                event.getAttemptUuid(), event.getMarksObtained(), event.getTotalMarksPossible());
        attemptService.applyGradedResult(event);
    }
}
