package com.quizmaster.attempt.messaging;

import com.quizmaster.attempt.event.GradingJob;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
public class AttemptEventPublisher {

    private final KafkaTemplate<String, Object> kafkaTemplate;

    /** Local-dev escape hatch: set app.kafka.enabled=false to run without a broker. */
    @Value("${app.kafka.enabled:true}")
    private boolean kafkaEnabled;

    /** Hand an attempt off to grading-service. Keyed by attemptUuid for ordering. */
    public void publishSubmitted(GradingJob job) {
        if (!kafkaEnabled) {
            log.warn("Kafka disabled — attempt {} submitted but NOT sent for grading", job.getAttemptUuid());
            return;
        }
        kafkaTemplate.send(KafkaTopics.ATTEMPT_SUBMITTED, job.getAttemptUuid(), job);
        log.info("Published attempt.submitted for attempt={}", job.getAttemptUuid());
    }
}
