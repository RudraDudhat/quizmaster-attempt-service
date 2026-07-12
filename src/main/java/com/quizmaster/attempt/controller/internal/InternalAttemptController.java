package com.quizmaster.attempt.controller.internal;

import com.quizmaster.attempt.event.GradingJob;
import com.quizmaster.attempt.service.AttemptService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * INTERNAL service-to-service API. Consumed by grading-service for the admin
 * essay-regrade flow, which needs the attempt's current answers (with their
 * already-computed marks) plus the quiz snapshot to recompute totals. NOT
 * gateway-routed — trusted network only.
 */
@RestController
@RequestMapping("/api/internal")
@RequiredArgsConstructor
public class InternalAttemptController {

    private final AttemptService attemptService;

    @GetMapping("/attempts/{attemptUuid}/grading-job")
    public ResponseEntity<GradingJob> getGradingJob(@PathVariable String attemptUuid) {
        return ResponseEntity.ok(attemptService.getGradingJob(attemptUuid));
    }
}
