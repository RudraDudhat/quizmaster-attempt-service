package com.quizmaster.attempt.messaging;

/** Canonical Kafka topic names shared across the QuizMaster event bus. */
public final class KafkaTopics {

    private KafkaTopics() {}

    public static final String ATTEMPT_SUBMITTED = "quiz.attempt.submitted";
    public static final String ATTEMPT_GRADED = "quiz.attempt.graded";
    public static final String ESSAY_GRADED = "quiz.essay.graded";
}
