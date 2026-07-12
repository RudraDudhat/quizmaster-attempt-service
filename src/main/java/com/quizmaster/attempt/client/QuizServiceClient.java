package com.quizmaster.attempt.client;

import com.quizmaster.attempt.client.dto.QuizSnapshot;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * Internal API of quiz-service, resolved through Eureka by application name
 * and load-balanced — no URL is configured anywhere.
 *
 * These calls bypass the api-gateway on purpose: service-to-service traffic
 * stays on the internal network, and /api/internal/** is never routed by the
 * gateway. Unlike the student catalog (which can degrade gracefully), there
 * is NO fallback here — an attempt genuinely cannot start if the quiz
 * definition can't be fetched, so a failure surfaces as a clear error.
 */
@FeignClient(name = "quizmaster-quiz-service", path = "/api/internal")
public interface QuizServiceClient {

    /** Full quiz definition (config + questions + options incl. correctness). */
    @GetMapping("/quizzes/{uuid}/snapshot")
    QuizSnapshot getQuizSnapshot(@PathVariable("uuid") String uuid);
}
