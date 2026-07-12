package com.quizmaster.attempt.controller.admin;

import com.quizmaster.attempt.dto.request.ResetAttemptsRequest;
import com.quizmaster.attempt.dto.response.ApiResponse;
import com.quizmaster.attempt.dto.response.AttemptReviewResponse;
import com.quizmaster.attempt.dto.response.PendingReviewResponse;
import com.quizmaster.attempt.dto.response.ResetAttemptsResponse;
import com.quizmaster.attempt.service.AttemptService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * Admin READ side of attempts: the pending essay-review queue and read-only
 * review. attempt-service owns the answer rows, so these live here.
 *
 * The grade WRITE moved to grading-service (POST /api/v1/admin/grading/...) —
 * grading recomputes the score and emits the graded event that attempt-service
 * then persists.
 */
@RestController
@RequestMapping("/api/v1/admin/attempts")
@PreAuthorize("hasAnyRole('ADMIN','SUPER_ADMIN')")
@RequiredArgsConstructor
public class AdminAttemptController {

    private final AttemptService attemptService;

    // ─── Pending essay-review queue ─────────────────────

    @GetMapping("/pending-reviews")
    public ResponseEntity<ApiResponse<Page<PendingReviewResponse>>> listPendingReviews(
            @PageableDefault(size = 20, sort = "submittedAt", direction = Sort.Direction.ASC) Pageable pageable) {
        Page<PendingReviewResponse> page = attemptService.listPendingReviews(pageable);
        return ResponseEntity.ok(ApiResponse.success("Pending reviews retrieved", page));
    }

    // ─── Admin review of an attempt (bypasses showCorrectAnswers) ──

    @GetMapping("/{attemptUuid}/review")
    public ResponseEntity<ApiResponse<AttemptReviewResponse>> getAttemptReview(
            @PathVariable String attemptUuid) {
        AttemptReviewResponse response = attemptService.getAttemptReviewAdmin(attemptUuid);
        return ResponseEntity.ok(ApiResponse.success("Attempt review retrieved", response));
    }

    // ─── Reset a student's attempts for a quiz ──────────
    // Lives here (not on auth-service's /admin/students) because the attempt
    // rows are owned here — keeps auth-service from having to call back into
    // attempt-service (which would form a dependency cycle).

    @PostMapping("/reset")
    public ResponseEntity<ApiResponse<ResetAttemptsResponse>> resetAttempts(
            @Valid @RequestBody ResetAttemptsRequest request) {
        ResetAttemptsResponse response =
                attemptService.resetAttempts(request.getStudentEmail(), request.getQuizUuid());
        return ResponseEntity.ok(ApiResponse.success("Attempts reset", response));
    }
}
