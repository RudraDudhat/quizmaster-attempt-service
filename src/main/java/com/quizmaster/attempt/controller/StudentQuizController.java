package com.quizmaster.attempt.controller;

import com.quizmaster.attempt.dto.request.AuditLogRequest;
import com.quizmaster.attempt.dto.request.SaveAnswerRequest;
import com.quizmaster.attempt.dto.request.StartAttemptRequest;
import com.quizmaster.attempt.dto.response.ApiResponse;
import com.quizmaster.attempt.dto.response.AttemptHistoryResponse;
import com.quizmaster.attempt.dto.response.AttemptResultResponse;
import com.quizmaster.attempt.dto.response.AttemptReviewResponse;
import com.quizmaster.attempt.dto.response.SaveAnswerResponse;
import com.quizmaster.attempt.dto.response.StartAttemptResponse;
import com.quizmaster.attempt.dto.response.SubmitAttemptResponse;
import com.quizmaster.attempt.service.AttemptService;
import com.quizmaster.attempt.util.HttpRequestUtils;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Student attempt API.
 *
 * All routes live under /api/v1/student/attempts so the gateway can send the
 * whole prefix here with a single predicate — without colliding with
 * quiz-service's /api/v1/student/quizzes catalog. Start therefore carries the
 * quizUuid in the body rather than the path.
 *
 * Identity arrives as X-User-* headers (the gateway has already verified the
 * JWT); the HeaderAuthenticationFilter turns them into the Authentication that
 * backs @PreAuthorize.
 */
@RestController
@RequestMapping("/api/v1/student/attempts")
@PreAuthorize("hasRole('STUDENT')")
@RequiredArgsConstructor
public class StudentQuizController {

    private final AttemptService attemptService;

    // ─── START ATTEMPT ──────────────────────────────────

    @PostMapping
    public ResponseEntity<ApiResponse<StartAttemptResponse>> startAttempt(
            @RequestHeader("X-User-Id") Long userId,
            @RequestHeader("X-User-Email") String email,
            @Valid @RequestBody StartAttemptRequest request,
            HttpServletRequest httpRequest) {
        // Honour X-Forwarded-For so we log the real client IP behind a proxy.
        String ipAddress = HttpRequestUtils.clientIp(httpRequest);
        String userAgent = httpRequest.getHeader("User-Agent");

        StartAttemptResponse response = attemptService.startAttempt(
                request.getQuizUuid(), userId, email, ipAddress, userAgent, request.getAccessCode());
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success("Attempt started", response));
    }

    // ─── SAVE ANSWER ────────────────────────────────────

    @PostMapping("/{attemptUuid}/answer")
    public ResponseEntity<ApiResponse<SaveAnswerResponse>> saveAnswer(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable String attemptUuid,
            @Valid @RequestBody SaveAnswerRequest request) {
        SaveAnswerResponse response = attemptService.saveAnswer(attemptUuid, request, userId);
        return ResponseEntity.ok(ApiResponse.success("Answer saved", response));
    }

    // ─── SUBMIT ATTEMPT ─────────────────────────────────

    @PostMapping("/{attemptUuid}/submit")
    public ResponseEntity<ApiResponse<SubmitAttemptResponse>> submitAttempt(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable String attemptUuid) {
        SubmitAttemptResponse response = attemptService.submitAttempt(attemptUuid, userId);
        return ResponseEntity.ok(ApiResponse.success("Attempt submitted", response));
    }

    // ─── GET ATTEMPT RESULT ─────────────────────────────

    @GetMapping("/{attemptUuid}/result")
    public ResponseEntity<ApiResponse<AttemptResultResponse>> getAttemptResult(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable String attemptUuid) {
        AttemptResultResponse response = attemptService.getAttemptResult(attemptUuid, userId);
        return ResponseEntity.ok(ApiResponse.success("Attempt result retrieved", response));
    }

    // ─── GET ATTEMPT REVIEW ─────────────────────────────

    @GetMapping("/{attemptUuid}/review")
    public ResponseEntity<ApiResponse<AttemptReviewResponse>> getAttemptReview(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable String attemptUuid) {
        AttemptReviewResponse response = attemptService.getAttemptReview(attemptUuid, userId);
        return ResponseEntity.ok(ApiResponse.success("Attempt review retrieved", response));
    }

    // ─── ATTEMPT HISTORY ────────────────────────────────

    @GetMapping("/history")
    public ResponseEntity<ApiResponse<Page<AttemptHistoryResponse>>> getAttemptHistory(
            @RequestHeader("X-User-Id") Long userId,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        Page<AttemptHistoryResponse> history = attemptService.getAttemptHistory(userId, pageable);
        return ResponseEntity.ok(ApiResponse.success("Attempt history retrieved", history));
    }

    // ─── LOG AUDIT EVENT ────────────────────────────────

    @PostMapping("/{attemptUuid}/audit")
    public ResponseEntity<ApiResponse<Void>> logAuditEvent(
            @RequestHeader("X-User-Id") Long userId,
            @PathVariable String attemptUuid,
            @Valid @RequestBody AuditLogRequest request) {
        attemptService.logAuditEvent(attemptUuid, userId, request);
        return ResponseEntity.ok(ApiResponse.success("Audit event logged"));
    }
}
