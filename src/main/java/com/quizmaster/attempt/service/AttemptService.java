package com.quizmaster.attempt.service;

import com.quizmaster.attempt.client.QuizServiceClient;
import com.quizmaster.attempt.client.dto.QuizSnapshot;
import com.quizmaster.attempt.client.dto.QuizSnapshot.SnapshotOption;
import com.quizmaster.attempt.client.dto.QuizSnapshot.SnapshotQuestion;
import com.quizmaster.attempt.dto.request.AuditLogRequest;
import com.quizmaster.attempt.dto.request.SaveAnswerRequest;
import com.quizmaster.attempt.dto.response.*;
import com.quizmaster.attempt.entity.AttemptAnswer;
import com.quizmaster.attempt.entity.AttemptAuditLog;
import com.quizmaster.attempt.entity.QuizAttempt;
import com.quizmaster.attempt.enums.AttemptStatus;
import com.quizmaster.attempt.event.AttemptGradedEvent;
import com.quizmaster.attempt.event.GradingJob;
import com.quizmaster.attempt.exception.BadRequestException;
import com.quizmaster.attempt.mapper.AttemptMapper;
import com.quizmaster.attempt.messaging.AttemptEventPublisher;
import com.quizmaster.attempt.repository.AttemptAnswerRepository;
import com.quizmaster.attempt.repository.AttemptAuditLogRepository;
import com.quizmaster.attempt.repository.QuizAttemptRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Attempt lifecycle: start, answer, submit, review. Scoring lives in
 * grading-service now — attempt-service does NO grading. On submit it
 * publishes {@code quiz.attempt.submitted} (carrying the snapshot + answers)
 * and later applies the result from {@code quiz.attempt.graded}.
 *
 * The quiz snapshot (captured once at start, stored on the attempt) is still
 * the source of truth for serving questions and for building the grading job,
 * so an in-flight attempt is immune to quiz edits and quiz-service downtime.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AttemptService {

    private final QuizAttemptRepository attemptRepository;
    private final AttemptAnswerRepository answerRepository;
    private final AttemptAuditLogRepository auditLogRepository;
    private final QuizServiceClient quizServiceClient;
    private final AttemptEventPublisher eventPublisher;
    private final AttemptMapper attemptMapper;
    private final ObjectMapper objectMapper;

    @Value("${app.attempt.max-tab-switches:5}")
    private int maxTabSwitches;

    // ═══════════════════════════════════════════════════════
    // START ATTEMPT
    // ═══════════════════════════════════════════════════════

    @Transactional
    public StartAttemptResponse startAttempt(String quizUuidRaw, Long studentUserId, String studentEmail,
                                             String ipAddress, String userAgent, String accessCode) {

        UUID quizUuid = parseUuidOrThrow(quizUuidRaw, "quizUuid");
        QuizSnapshot snap = fetchSnapshot(quizUuidRaw);

        if (!"PUBLISHED".equals(snap.getStatus())) {
            throw new BadRequestException("Quiz is not available for attempts");
        }

        Instant now = Instant.now();
        if (snap.getStartsAt() != null && now.isBefore(snap.getStartsAt())) {
            throw new BadRequestException("Quiz has not started yet. Starts at: " + snap.getStartsAt());
        }
        if (snap.getExpiresAt() != null && now.isAfter(snap.getExpiresAt())) {
            throw new BadRequestException("Quiz has expired");
        }

        if (snap.getAccessCode() != null && !snap.getAccessCode().isEmpty()) {
            if (accessCode == null || !snap.getAccessCode().equals(accessCode)) {
                throw new BadRequestException("Invalid access code");
            }
        }

        Optional<QuizAttempt> activeAttempt = attemptRepository.findActiveAttempt(quizUuid, studentUserId);
        if (activeAttempt.isPresent()) {
            return buildStartResponse(activeAttempt.get(), snap);
        }

        int attemptCount = attemptRepository.countValidAttempts(quizUuid, studentUserId);
        if (snap.getMaxAttempts() != null && snap.getMaxAttempts() > 0 && attemptCount >= snap.getMaxAttempts()) {
            throw new BadRequestException("Max attempts reached");
        }

        if (snap.getCooldownHours() != null && snap.getCooldownHours() > 0 && attemptCount > 0) {
            Optional<Instant> lastSubmitted = attemptRepository.findLastSubmittedAt(quizUuid, studentUserId);
            if (lastSubmitted.isPresent()) {
                Instant cooldownEnd = lastSubmitted.get().plus(Duration.ofHours(snap.getCooldownHours()));
                if (now.isBefore(cooldownEnd)) {
                    long hoursLeft = Duration.between(now, cooldownEnd).toHours() + 1;
                    throw new BadRequestException("Cooldown active. Try again in " + hoursLeft + " hour(s)");
                }
            }
        }

        Instant deadlineAt;
        if (snap.getTimeLimitSeconds() != null && snap.getTimeLimitSeconds() > 0) {
            int totalSeconds = snap.getTimeLimitSeconds()
                    + (snap.getGracePeriodSeconds() != null ? snap.getGracePeriodSeconds() : 0);
            deadlineAt = now.plusSeconds(totalSeconds);
        } else {
            deadlineAt = snap.getExpiresAt() != null ? snap.getExpiresAt() : now.plusSeconds(86400);
        }

        List<SnapshotQuestion> allQuestions = snap.getQuestions() == null ? List.of() : snap.getQuestions();
        if (allQuestions.isEmpty()) {
            throw new BadRequestException("Quiz has no questions");
        }
        List<SnapshotQuestion> served = selectQuestions(snap, allQuestions);

        BigDecimal totalMarksPossible = served.stream()
                .map(SnapshotQuestion::getMarks)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        String questionOrder = served.stream()
                .map(q -> String.valueOf(q.getQuizQuestionId()))
                .collect(Collectors.joining(","));

        QuizAttempt attempt = QuizAttempt.builder()
                .quizUuid(quizUuid)
                .studentUserId(studentUserId)
                .studentEmail(studentEmail)
                .quizTitle(snap.getTitle())
                .passMarks(snap.getPassMarks())
                .quizSnapshotJson(writeJson(snap))
                .attemptNumber(attemptCount + 1)
                .status(AttemptStatus.IN_PROGRESS)
                .startedAt(now)
                .deadlineAt(deadlineAt)
                .totalMarksPossible(totalMarksPossible)
                .questionOrder(questionOrder)
                .ipAddress(ipAddress)
                .userAgent(userAgent)
                .build();

        attempt = attemptRepository.save(attempt);

        return attemptMapper.toStartResponse(attempt, snap, served);
    }

    // ═══════════════════════════════════════════════════════
    // SAVE ANSWER
    // ═══════════════════════════════════════════════════════

    @Transactional
    public SaveAnswerResponse saveAnswer(String attemptUuid, SaveAnswerRequest request, Long studentUserId) {
        QuizAttempt attempt = attemptRepository
                .findByUuidAndStudentUserId(UUID.fromString(attemptUuid), studentUserId)
                .orElseThrow(() -> new BadRequestException("Attempt not found"));

        if (attempt.getStatus() != AttemptStatus.IN_PROGRESS) {
            throw new BadRequestException("Attempt is no longer active (status: " + attempt.getStatus() + ")");
        }

        if (attempt.getDeadlineAt() != null && Instant.now().isAfter(attempt.getDeadlineAt())) {
            autoSubmitAttempt(attempt);
            throw new BadRequestException("Time expired. Quiz has been auto-submitted.");
        }

        QuizSnapshot snap = readSnapshot(attempt);
        SnapshotQuestion sq = snap.getQuestions().stream()
                .filter(q -> request.getQuizQuestionUuid().equals(q.getQuizQuestionUuid()))
                .findFirst()
                .orElseThrow(() -> new BadRequestException("Quiz question not found"));

        AttemptAnswer answer = answerRepository
                .findByAttemptIdAndQuizQuestionId(attempt.getId(), sq.getQuizQuestionId())
                .orElse(null);

        boolean wasHintUsed = answer != null && Boolean.TRUE.equals(answer.getHintUsed());

        if (answer == null) {
            answer = AttemptAnswer.builder()
                    .attempt(attempt)
                    .quizQuestionId(sq.getQuizQuestionId())
                    .questionUuid(sq.getQuestionUuid())
                    .questionType(sq.getQuestionType())
                    .build();
        }

        answer.setTextAnswer(request.getTextAnswer());
        answer.setBooleanAnswer(request.getBooleanAnswer());
        answer.setHintUsed(Boolean.TRUE.equals(request.getHintUsed()));
        answer.setTimeSpentSeconds(request.getTimeSpentSeconds() != null ? request.getTimeSpentSeconds() : 0);
        answer.setIsFlagged(Boolean.TRUE.equals(request.getIsFlagged()));
        answer.setSelectedOptionIds(toJsonString(request.getSelectedOptionUuids()));
        answer.setOrderedOptionIds(toJsonString(request.getOrderedOptionUuids()));
        answer.setMatchPairs(toJsonString(request.getMatchPairs()));

        boolean empty = isBlankResponse(request);
        answer.setIsSkipped(empty);
        answer.setAnsweredAt(empty ? null : Instant.now());

        if (Boolean.TRUE.equals(request.getHintUsed()) && !wasHintUsed) {
            saveAuditLog(attempt, "HINT_REVEALED", null);
        }

        answerRepository.save(answer);

        return attemptMapper.toSaveAnswerResponse(request.getQuizQuestionUuid());
    }

    // ═══════════════════════════════════════════════════════
    // SUBMIT / AUTO-SUBMIT  (hand off to grading-service)
    // ═══════════════════════════════════════════════════════

    @Transactional
    public SubmitAttemptResponse submitAttempt(String attemptUuid, Long studentUserId) {
        QuizAttempt attempt = attemptRepository
                .findByUuidAndStudentUserId(UUID.fromString(attemptUuid), studentUserId)
                .orElseThrow(() -> new BadRequestException("Attempt not found"));

        if (attempt.getStatus() != AttemptStatus.IN_PROGRESS) {
            throw new BadRequestException("Attempt already submitted or expired");
        }

        finalizeAndPublish(attempt, AttemptStatus.SUBMITTED);
        return attemptMapper.toSubmitResponse(attempt);
    }

    @Transactional
    public QuizAttempt autoSubmitAttempt(QuizAttempt attempt) {
        if (attempt.getStatus() != AttemptStatus.IN_PROGRESS)
            return attempt;
        finalizeAndPublish(attempt, AttemptStatus.AUTO_SUBMITTED);
        // TODO(kafka): a dedicated AttemptAutoSubmittedEvent could notify the
        // student their timed quiz closed; for now the graded event carries the
        // autoSubmitted flag downstream.
        log.info("Submitted attempt {} for grading (status={}, student={})",
                attempt.getId(), attempt.getStatus(), attempt.getStudentUserId());
        return attempt;
    }

    /**
     * Materialise skipped answers, mark the attempt submitted, and publish the
     * grading job. NO scoring happens here — grading-service does that and the
     * result comes back on quiz.attempt.graded.
     */
    private void finalizeAndPublish(QuizAttempt attempt, AttemptStatus status) {
        QuizSnapshot snap = readSnapshot(attempt);
        createSkippedAnswers(attempt, snap);
        attempt.setStatus(status);
        attempt.setSubmittedAt(Instant.now());
        attemptRepository.saveAndFlush(attempt);

        List<AttemptAnswer> answers = answerRepository.findByAttemptId(attempt.getId());
        eventPublisher.publishSubmitted(buildGradingJob(attempt, snap, answers,
                status == AttemptStatus.AUTO_SUBMITTED));
    }

    // ═══════════════════════════════════════════════════════
    // APPLY GRADE  (consumed from quiz.attempt.graded)
    // ═══════════════════════════════════════════════════════

    @Transactional
    public void applyGradedResult(AttemptGradedEvent event) {
        QuizAttempt attempt = attemptRepository.findByUuid(UUID.fromString(event.getAttemptUuid()))
                .orElse(null);
        if (attempt == null) {
            log.warn("Received grade for unknown attempt {}", event.getAttemptUuid());
            return;
        }

        // Apply per-answer marks
        Map<Long, AttemptGradedEvent.AnswerResult> byQq = (event.getResults() == null ? List.<AttemptGradedEvent.AnswerResult>of() : event.getResults())
                .stream().collect(Collectors.toMap(AttemptGradedEvent.AnswerResult::getQuizQuestionId, r -> r, (a, b) -> b));

        List<AttemptAnswer> answers = answerRepository.findByAttemptId(attempt.getId());
        for (AttemptAnswer ans : answers) {
            AttemptGradedEvent.AnswerResult r = byQq.get(ans.getQuizQuestionId());
            if (r != null) {
                ans.setIsCorrect(r.getIsCorrect());
                ans.setMarksAwarded(r.getMarksAwarded());
            }
        }
        answerRepository.saveAll(answers);

        // Apply attempt totals
        attempt.setMarksObtained(event.getMarksObtained());
        attempt.setPositiveMarks(event.getPositiveMarks());
        attempt.setNegativeMarksDeducted(event.getNegativeMarksDeducted());
        attempt.setPercentage(event.getPercentage());
        attempt.setIsPassed(event.getIsPassed());
        attempt.setGradedAt(Instant.now());

        // Essays still awaiting manual grading: store the provisional marks but
        // do not rank the attempt yet. Ranks are recomputed once the last
        // essay is graded (the event is re-emitted with pendingReviewCount = 0).
        if (event.getPendingReviewCount() > 0) {
            attempt.setRank(null);
            attemptRepository.saveAndFlush(attempt);
            log.info("Attempt {} has {} essay(s) pending review — result not released yet",
                    event.getAttemptUuid(), event.getPendingReviewCount());
            return;
        }

        int betterCount = attemptRepository.countBetterAttempts(attempt.getQuizUuid(), attempt.getMarksObtained());
        attempt.setRank(betterCount + 1);

        attemptRepository.saveAndFlush(attempt);
        attemptRepository.recomputeRanksForQuiz(attempt.getQuizUuid());
    }

    // ═══════════════════════════════════════════════════════
    // GRADING JOB (internal API for admin essay-regrade)
    // ═══════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public GradingJob getGradingJob(String attemptUuid) {
        QuizAttempt attempt = attemptRepository.findByUuid(UUID.fromString(attemptUuid))
                .orElseThrow(() -> new BadRequestException("Attempt not found"));
        QuizSnapshot snap = readSnapshot(attempt);
        List<AttemptAnswer> answers = answerRepository.findByAttemptId(attempt.getId());
        return buildGradingJob(attempt, snap, answers,
                attempt.getStatus() == AttemptStatus.AUTO_SUBMITTED);
    }

    private GradingJob buildGradingJob(QuizAttempt attempt, QuizSnapshot snap,
                                       List<AttemptAnswer> answers, boolean autoSubmitted) {

        List<GradingJob.JobQuestion> jobQuestions = (snap.getQuestions() == null ? List.<SnapshotQuestion>of() : snap.getQuestions())
                .stream()
                .map(q -> GradingJob.JobQuestion.builder()
                        .quizQuestionId(q.getQuizQuestionId())
                        .questionUuid(q.getQuestionUuid() != null ? q.getQuestionUuid().toString() : null)
                        .questionType(q.getQuestionType())
                        .marks(q.getMarks())
                        .negativeMarks(q.getNegativeMarks())
                        .hintMarkDeduction(q.getHintMarkDeduction())
                        .options((q.getOptions() == null ? List.<SnapshotOption>of() : q.getOptions()).stream()
                                .map(o -> GradingJob.JobOption.builder()
                                        .uuid(o.getUuid() != null ? o.getUuid().toString() : null)
                                        .optionText(o.getOptionText())
                                        .optionOrder(o.getOptionOrder())
                                        .isCorrect(o.getIsCorrect())
                                        .matchPairKey(o.getMatchPairKey())
                                        .matchPairVal(o.getMatchPairVal())
                                        .build())
                                .collect(Collectors.toList()))
                        .build())
                .collect(Collectors.toList());

        List<GradingJob.JobAnswer> jobAnswers = answers.stream()
                .map(a -> GradingJob.JobAnswer.builder()
                        .quizQuestionId(a.getQuizQuestionId())
                        .questionUuid(a.getQuestionUuid() != null ? a.getQuestionUuid().toString() : null)
                        .questionType(a.getQuestionType())
                        .selectedOptionIds(a.getSelectedOptionIds())
                        .textAnswer(a.getTextAnswer())
                        .orderedOptionIds(a.getOrderedOptionIds())
                        .matchPairs(a.getMatchPairs())
                        .booleanAnswer(a.getBooleanAnswer())
                        .hintUsed(a.getHintUsed())
                        .isSkipped(a.getIsSkipped())
                        .marksAwarded(a.getMarksAwarded())
                        .isCorrect(a.getIsCorrect())
                        .build())
                .collect(Collectors.toList());

        return GradingJob.builder()
                .attemptUuid(attempt.getUuid().toString())
                .quizUuid(attempt.getQuizUuid().toString())
                .studentUserId(attempt.getStudentUserId())
                .studentEmail(attempt.getStudentEmail())
                .quizTitle(attempt.getQuizTitle())
                .passMarks(attempt.getPassMarks())
                .totalMarksPossible(attempt.getTotalMarksPossible())
                .autoSubmitted(autoSubmitted)
                .questions(jobQuestions)
                .answers(jobAnswers)
                .build();
    }

    // ═══════════════════════════════════════════════════════
    // RESULT / REVIEW / HISTORY
    // ═══════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public AttemptResultResponse getAttemptResult(String attemptUuid, Long studentUserId) {
        QuizAttempt attempt = attemptRepository
                .findByUuidAndStudentUserId(UUID.fromString(attemptUuid), studentUserId)
                .orElseThrow(() -> new BadRequestException("Attempt not found"));

        if (attempt.getStatus() == AttemptStatus.IN_PROGRESS) {
            throw new BadRequestException("Attempt is still in progress");
        }

        List<AttemptAnswer> answers = answerRepository.findByAttemptId(attempt.getId());
        int correctCount = (int) answers.stream().filter(a -> Boolean.TRUE.equals(a.getIsCorrect())).count();
        int skippedCount = (int) answers.stream().filter(a -> Boolean.TRUE.equals(a.getIsSkipped())).count();
        int pendingReviewCount = (int) answers.stream()
                .filter(a -> a.getIsCorrect() == null && !Boolean.TRUE.equals(a.getIsSkipped()))
                .count();
        int wrongCount = answers.size() - correctCount - skippedCount - pendingReviewCount;

        AttemptResultResponse res = attemptMapper.toResultResponse(
                attempt, correctCount, wrongCount, skippedCount, pendingReviewCount);

        // Don't release the score until every essay has been graded.
        if (pendingReviewCount > 0) {
            res.setMarksObtained(null);
            res.setPositiveMarks(null);
            res.setNegativeMarksDeducted(null);
            res.setPercentage(null);
            res.setIsPassed(null);
            res.setRank(null);
        }
        return res;
    }

    @Transactional(readOnly = true)
    public AttemptReviewResponse getAttemptReview(String attemptUuid, Long studentUserId) {
        QuizAttempt attempt = attemptRepository
                .findByUuidAndStudentUserId(UUID.fromString(attemptUuid), studentUserId)
                .orElseThrow(() -> new BadRequestException("Attempt not found"));

        if (attempt.getStatus() == AttemptStatus.IN_PROGRESS) {
            throw new BadRequestException("Attempt is still in progress");
        }

        if (answerRepository.countPendingReviewByAttemptId(attempt.getId()) > 0) {
            throw new BadRequestException(
                    "Result not released yet — some answers are still awaiting grading");
        }

        QuizSnapshot snap = readSnapshot(attempt);
        if (!Boolean.TRUE.equals(snap.getShowCorrectAnswers())) {
            throw new BadRequestException("Review not available for this quiz");
        }

        List<AttemptAnswer> answers = answerRepository.findByAttemptId(attempt.getId());
        return attemptMapper.toReviewResponse(attempt, answers, questionsById(snap), this::parseJsonUuidList);
    }

    @Transactional(readOnly = true)
    public Page<AttemptHistoryResponse> getAttemptHistory(Long studentUserId, Pageable pageable) {
        return attemptRepository.findByStudentUserIdOrderByCreatedAtDesc(studentUserId, pageable)
                .map(att -> {
                    AttemptHistoryResponse res = attemptMapper.toHistoryResponse(att);
                    boolean pending = answerRepository.countPendingReviewByAttemptId(att.getId()) > 0;
                    res.setHasPendingReview(pending);
                    if (pending) {
                        res.setMarksObtained(null);
                        res.setPercentage(null);
                        res.setIsPassed(null);
                    }
                    return res;
                });
    }

    // ═══════════════════════════════════════════════════════
    // ADMIN: read-only review + pending-essay queue
    // (the grade WRITE itself lives in grading-service)
    // ═══════════════════════════════════════════════════════

    @Transactional(readOnly = true)
    public Page<PendingReviewResponse> listPendingReviews(Pageable pageable) {
        return attemptRepository.findAttemptsPendingReview(pageable)
                .map(att -> {
                    int pending = answerRepository.countPendingReviewByAttemptId(att.getId());
                    int total = answerRepository.findByAttemptId(att.getId()).size();
                    return PendingReviewResponse.builder()
                            .attemptUuid(att.getUuid().toString())
                            .quizUuid(att.getQuizUuid().toString())
                            .quizTitle(att.getQuizTitle())
                            .studentEmail(att.getStudentEmail())
                            .submittedAt(att.getSubmittedAt())
                            .pendingCount(pending)
                            .totalQuestions(total)
                            .build();
                });
    }

    @Transactional
    public ResetAttemptsResponse resetAttempts(String studentEmail, String quizUuidRaw) {
        UUID quizUuid = parseUuidOrThrow(quizUuidRaw, "quizUuid");

        List<QuizAttempt> attempts = attemptRepository.findByStudentEmailAndQuizUuidAndStatusIn(
                studentEmail, quizUuid,
                List.of(AttemptStatus.IN_PROGRESS, AttemptStatus.SUBMITTED, AttemptStatus.AUTO_SUBMITTED));

        String quizTitle = attempts.isEmpty() ? null : attempts.get(0).getQuizTitle();
        for (QuizAttempt a : attempts) {
            a.setStatus(AttemptStatus.INVALIDATED);
            a.setInvalidationReason("Reset by admin");
        }
        attemptRepository.saveAll(attempts);

        // TODO(kafka): publish AttemptsResetEvent so notification-service can
        // tell the student they may retake the quiz.
        log.info("Admin reset {} attempt(s) for student {} on quiz {}",
                attempts.size(), studentEmail, quizUuid);

        return ResetAttemptsResponse.builder()
                .studentEmail(studentEmail)
                .quizUuid(quizUuidRaw)
                .quizTitle(quizTitle)
                .resetCount(attempts.size())
                .message(attempts.size() + " attempt(s) invalidated successfully")
                .build();
    }

    @Transactional(readOnly = true)
    public AttemptReviewResponse getAttemptReviewAdmin(String attemptUuid) {
        QuizAttempt attempt = attemptRepository.findByUuid(UUID.fromString(attemptUuid))
                .orElseThrow(() -> new BadRequestException("Attempt not found"));
        if (attempt.getStatus() == AttemptStatus.IN_PROGRESS) {
            throw new BadRequestException("Attempt is still in progress");
        }
        QuizSnapshot snap = readSnapshot(attempt);
        List<AttemptAnswer> answers = answerRepository.findByAttemptId(attempt.getId());
        return attemptMapper.toReviewResponse(attempt, answers, questionsById(snap), this::parseJsonUuidList);
    }

    // ═══════════════════════════════════════════════════════
    // AUDIT
    // ═══════════════════════════════════════════════════════

    @Transactional
    public void logAuditEvent(String attemptUuid, Long studentUserId, AuditLogRequest request) {
        QuizAttempt attempt = attemptRepository
                .findByUuidAndStudentUserId(UUID.fromString(attemptUuid), studentUserId)
                .orElseThrow(() -> new BadRequestException("Attempt not found"));

        if (attempt.getStatus() != AttemptStatus.IN_PROGRESS) {
            throw new BadRequestException("Cannot log events for a completed attempt");
        }

        String eventDataJson = null;
        if (request.getEventData() != null) {
            try {
                eventDataJson = objectMapper.writeValueAsString(request.getEventData());
            } catch (Exception e) {
                log.warn("Failed to serialize audit event data: {}", e.getMessage());
            }
        }

        saveAuditLog(attempt, request.getEventType(), eventDataJson);

        if ("TAB_SWITCH".equals(request.getEventType())) {
            attempt.setTabSwitchCount(attempt.getTabSwitchCount() + 1);
        }
        if ("FULLSCREEN_EXIT".equals(request.getEventType())) {
            attempt.setFullscreenExitCount(attempt.getFullscreenExitCount() + 1);
        }
        if (attempt.getTabSwitchCount() >= maxTabSwitches) {
            attempt.setIsFlaggedSuspicious(true);
        }

        attemptRepository.save(attempt);
    }

    // ═══════════════════════════════════════════════════════
    // HELPERS
    // ═══════════════════════════════════════════════════════

    private QuizSnapshot fetchSnapshot(String quizUuid) {
        try {
            QuizSnapshot snap = quizServiceClient.getQuizSnapshot(quizUuid);
            if (snap == null) {
                throw new BadRequestException("Quiz not found");
            }
            return snap;
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            log.error("Failed to fetch quiz snapshot for {}: {}", quizUuid, e.getMessage());
            throw new BadRequestException(
                    "Unable to start attempt right now — the quiz service is unavailable. Please try again.");
        }
    }

    private QuizSnapshot readSnapshot(QuizAttempt attempt) {
        try {
            return objectMapper.readValue(attempt.getQuizSnapshotJson(), QuizSnapshot.class);
        } catch (Exception e) {
            log.error("Corrupt quiz snapshot on attempt {}: {}", attempt.getId(), e.getMessage());
            throw new BadRequestException("Attempt data is corrupt; cannot continue");
        }
    }

    private Map<Long, SnapshotQuestion> questionsById(QuizSnapshot snap) {
        if (snap.getQuestions() == null)
            return Map.of();
        return snap.getQuestions().stream()
                .collect(Collectors.toMap(SnapshotQuestion::getQuizQuestionId, q -> q, (a, b) -> a));
    }

    private void createSkippedAnswers(QuizAttempt attempt, QuizSnapshot snap) {
        List<Long> questionOrderIds = parseCommaSeparatedLongs(attempt.getQuestionOrder());
        if (questionOrderIds == null)
            return;
        Map<Long, SnapshotQuestion> qMap = questionsById(snap);

        for (Long qqId : questionOrderIds) {
            boolean exists = answerRepository.findByAttemptIdAndQuizQuestionId(attempt.getId(), qqId).isPresent();
            if (!exists) {
                SnapshotQuestion sq = qMap.get(qqId);
                if (sq == null)
                    continue;
                AttemptAnswer skipped = AttemptAnswer.builder()
                        .attempt(attempt)
                        .quizQuestionId(qqId)
                        .questionUuid(sq.getQuestionUuid())
                        .questionType(sq.getQuestionType())
                        .isSkipped(true)
                        .marksAwarded(BigDecimal.ZERO)
                        .build();
                answerRepository.save(skipped);
            }
        }
    }

    private void saveAuditLog(QuizAttempt attempt, String eventType, String eventData) {
        AttemptAuditLog auditLog = AttemptAuditLog.builder()
                .attempt(attempt)
                .eventType(eventType)
                .eventData(eventData)
                .occurredAt(Instant.now())
                .build();
        auditLogRepository.save(auditLog);
    }

    private List<SnapshotQuestion> selectQuestions(QuizSnapshot snap, List<SnapshotQuestion> allQuestions) {
        List<SnapshotQuestion> pool = new ArrayList<>(allQuestions);
        if (Boolean.TRUE.equals(snap.getShuffleQuestions())) {
            Collections.shuffle(pool);
        }
        if (snap.getQuestionsToServe() != null && snap.getQuestionsToServe() < pool.size()) {
            pool = pool.subList(0, snap.getQuestionsToServe());
        }
        return pool;
    }

    private StartAttemptResponse buildStartResponse(QuizAttempt attempt, QuizSnapshot snap) {
        List<Long> servedIds = parseCommaSeparatedLongs(attempt.getQuestionOrder());
        Map<Long, SnapshotQuestion> qMap = questionsById(snap);
        List<SnapshotQuestion> served;
        if (servedIds == null) {
            served = snap.getQuestions() == null ? List.of() : snap.getQuestions();
        } else {
            served = servedIds.stream().map(qMap::get).filter(Objects::nonNull).collect(Collectors.toList());
        }
        return attemptMapper.toStartResponse(attempt, snap, served);
    }

    private boolean isBlankResponse(SaveAnswerRequest request) {
        return (request.getSelectedOptionUuids() == null || request.getSelectedOptionUuids().isEmpty())
                && (request.getTextAnswer() == null || request.getTextAnswer().isBlank())
                && request.getBooleanAnswer() == null
                && (request.getOrderedOptionUuids() == null || request.getOrderedOptionUuids().isEmpty())
                && (request.getMatchPairs() == null || request.getMatchPairs().isEmpty());
    }

    // ─── JSON helpers ───────────────────────────────────

    private String writeJson(Object obj) {
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            throw new BadRequestException("Failed to serialize quiz snapshot");
        }
    }

    private String toJsonString(Object obj) {
        if (obj == null)
            return null;
        try {
            return objectMapper.writeValueAsString(obj);
        } catch (Exception e) {
            return obj.toString();
        }
    }

    private List<UUID> parseJsonUuidList(String json) {
        if (json == null || json.isBlank())
            return null;
        try {
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, UUID.class));
        } catch (Exception e) {
            return null;
        }
    }

    private List<Long> parseCommaSeparatedLongs(String s) {
        if (s == null || s.isBlank())
            return null;
        List<Long> parsed = new ArrayList<>();
        for (String token : s.split(",")) {
            String trimmed = token.trim();
            if (trimmed.isEmpty())
                continue;
            try {
                parsed.add(Long.parseLong(trimmed));
            } catch (NumberFormatException ex) {
                log.warn("Skipping non-numeric question_order token: {}", trimmed);
            }
        }
        return parsed.isEmpty() ? null : parsed;
    }

    private UUID parseUuidOrThrow(String raw, String fieldName) {
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ex) {
            throw new BadRequestException("Invalid " + fieldName + " format");
        }
    }
}
