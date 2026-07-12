package com.quizmaster.attempt.mapper;

import com.quizmaster.attempt.client.dto.QuizSnapshot.SnapshotOption;
import com.quizmaster.attempt.client.dto.QuizSnapshot;
import com.quizmaster.attempt.client.dto.QuizSnapshot.SnapshotQuestion;
import com.quizmaster.attempt.dto.response.*;
import com.quizmaster.attempt.entity.AttemptAnswer;
import com.quizmaster.attempt.entity.QuizAttempt;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Plain (non-MapStruct) mapper. Written by hand because the build does not
 * run the MapStruct annotation processor and, more importantly, every
 * question/option field now comes from the deserialized quiz snapshot rather
 * than a JPA entity graph.
 */
@Component
public class AttemptMapper {

    // ─── START ─────────────────────────────────────────

    public StartAttemptResponse toStartResponse(QuizAttempt attempt, QuizSnapshot snap,
            List<SnapshotQuestion> served) {
        List<StartAttemptResponse.AttemptQuestionDto> questions = served.stream()
                .map(this::toAttemptQuestionDto)
                .collect(Collectors.toList());

        return StartAttemptResponse.builder()
                .attemptUuid(attempt.getUuid().toString())
                .quizUuid(attempt.getQuizUuid().toString())
                .quizTitle(snap.getTitle())
                .deadlineAt(attempt.getDeadlineAt())
                .timeLimitSeconds(snap.getTimeLimitSeconds())
                .questions(questions)
                .build();
    }

    private StartAttemptResponse.AttemptQuestionDto toAttemptQuestionDto(SnapshotQuestion q) {
        // Student view — NEVER expose isCorrect.
        List<StartAttemptResponse.OptionDto> options = options(q).stream()
                .sorted(optionOrder())
                .map(o -> StartAttemptResponse.OptionDto.builder()
                        .uuid(o.getUuid())
                        .optionText(o.getOptionText())
                        .mediaUrl(o.getMediaUrl())
                        .optionOrder(o.getOptionOrder())
                        .build())
                .collect(Collectors.toList());

        return StartAttemptResponse.AttemptQuestionDto.builder()
                .quizQuestionUuid(q.getQuizQuestionUuid())
                .questionUuid(q.getQuestionUuid() != null ? q.getQuestionUuid().toString() : null)
                .questionText(q.getQuestionText())
                .questionType(q.getQuestionType())
                .difficulty(q.getDifficulty())
                .marks(q.getMarks())
                .negativeMarks(q.getNegativeMarks())
                .perQuestionSecs(q.getPerQuestionSecs())
                .displayOrder(q.getDisplayOrder())
                .hintText(q.getHintText())
                .mediaUrl(q.getMediaUrl())
                .codeContent(q.getCodeContent())
                .codeLanguage(q.getCodeLanguage())
                .options(options)
                .build();
    }

    // ─── SUBMIT ────────────────────────────────────────

    public SubmitAttemptResponse toSubmitResponse(QuizAttempt attempt) {
        return SubmitAttemptResponse.builder()
                .attemptUuid(attempt.getUuid().toString())
                .quizUuid(attempt.getQuizUuid().toString())
                .quizTitle(attempt.getQuizTitle())
                .marksObtained(attempt.getMarksObtained())
                .totalMarksPossible(attempt.getTotalMarksPossible())
                .percentage(attempt.getPercentage())
                .isPassed(attempt.getIsPassed())
                .passMarks(attempt.getPassMarks())
                .timeTakenSeconds(timeTaken(attempt))
                .submittedAt(attempt.getSubmittedAt())
                .attemptNumber(attempt.getAttemptNumber())
                .status(attempt.getStatus().name())
                .build();
    }

    // ─── RESULT ────────────────────────────────────────

    public AttemptResultResponse toResultResponse(QuizAttempt attempt, int correctCount,
            int wrongCount, int skippedCount, int pendingReviewCount) {
        return AttemptResultResponse.builder()
                .attemptUuid(attempt.getUuid().toString())
                .quizUuid(attempt.getQuizUuid().toString())
                .quizTitle(attempt.getQuizTitle())
                .marksObtained(attempt.getMarksObtained())
                .totalMarksPossible(attempt.getTotalMarksPossible())
                .percentage(attempt.getPercentage())
                .isPassed(attempt.getIsPassed())
                .passMarks(attempt.getPassMarks())
                .timeTakenSeconds(timeTaken(attempt))
                .submittedAt(attempt.getSubmittedAt())
                .attemptNumber(attempt.getAttemptNumber())
                .status(attempt.getStatus().name())
                .rank(attempt.getRank())
                .positiveMarks(attempt.getPositiveMarks())
                .negativeMarksDeducted(attempt.getNegativeMarksDeducted())
                .correctCount(correctCount)
                .wrongCount(wrongCount)
                .skippedCount(skippedCount)
                .pendingReviewCount(pendingReviewCount)
                .build();
    }

    // ─── HISTORY ───────────────────────────────────────

    public AttemptHistoryResponse toHistoryResponse(QuizAttempt attempt) {
        return AttemptHistoryResponse.builder()
                .attemptUuid(attempt.getUuid().toString())
                .quizUuid(attempt.getQuizUuid().toString())
                .quizTitle(attempt.getQuizTitle())
                .attemptNumber(attempt.getAttemptNumber())
                .status(attempt.getStatus().name())
                .percentage(attempt.getPercentage())
                .isPassed(attempt.getIsPassed())
                .marksObtained(attempt.getMarksObtained())
                .totalMarksPossible(attempt.getTotalMarksPossible())
                .startedAt(attempt.getStartedAt())
                .submittedAt(attempt.getSubmittedAt())
                .timeTakenSeconds(timeTaken(attempt))
                .build();
    }

    // ─── REVIEW ────────────────────────────────────────

    public AttemptReviewResponse toReviewResponse(QuizAttempt attempt, List<AttemptAnswer> answers,
            Map<Long, SnapshotQuestion> qMap, Function<String, List<UUID>> jsonParser) {
        List<AttemptReviewResponse.ReviewQuestionDto> questions = answers.stream()
                .map(ans -> toReviewQuestionDto(ans, qMap.get(ans.getQuizQuestionId()), jsonParser))
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        return AttemptReviewResponse.builder()
                .attemptUuid(attempt.getUuid().toString())
                .quizTitle(attempt.getQuizTitle())
                .questions(questions)
                .build();
    }

    private AttemptReviewResponse.ReviewQuestionDto toReviewQuestionDto(AttemptAnswer ans,
            SnapshotQuestion q, Function<String, List<UUID>> jsonParser) {
        if (q == null)
            return null;

        List<UUID> correctOptionUuids = options(q).stream()
                .filter(o -> Boolean.TRUE.equals(o.getIsCorrect()))
                .map(SnapshotOption::getUuid)
                .collect(Collectors.toList());

        List<AttemptReviewResponse.ReviewOptionDto> options = options(q).stream()
                .sorted(optionOrder())
                .map(o -> AttemptReviewResponse.ReviewOptionDto.builder()
                        .uuid(o.getUuid())
                        .optionText(o.getOptionText())
                        .mediaUrl(o.getMediaUrl())
                        .optionOrder(o.getOptionOrder())
                        .isCorrect(o.getIsCorrect())
                        .build())
                .collect(Collectors.toList());

        return AttemptReviewResponse.ReviewQuestionDto.builder()
                .questionUuid(q.getQuestionUuid() != null ? q.getQuestionUuid().toString() : null)
                .questionText(q.getQuestionText())
                .questionType(q.getQuestionType())
                .marks(q.getMarks())
                .marksAwarded(ans.getMarksAwarded())
                .studentSelectedOptionUuids(jsonParser.apply(ans.getSelectedOptionIds()))
                .correctOptionUuids(correctOptionUuids)
                .textAnswer(ans.getTextAnswer())
                .isCorrect(ans.getIsCorrect())
                .isSkipped(ans.getIsSkipped())
                .hintUsed(ans.getHintUsed())
                .timeSpentSeconds(ans.getTimeSpentSeconds())
                .explanation(q.getExplanation())
                .codeContent(q.getCodeContent())
                .codeLanguage(q.getCodeLanguage())
                .mediaUrl(q.getMediaUrl())
                .options(options)
                .build();
    }

    // ─── SAVE ANSWER ───────────────────────────────────

    public SaveAnswerResponse toSaveAnswerResponse(UUID quizQuestionUuid) {
        return SaveAnswerResponse.builder()
                .quizQuestionUuid(quizQuestionUuid)
                .savedAt(Instant.now())
                .build();
    }

    // ─── helpers ───────────────────────────────────────

    private static Long timeTaken(QuizAttempt attempt) {
        if (attempt.getSubmittedAt() != null && attempt.getStartedAt() != null) {
            return Duration.between(attempt.getStartedAt(), attempt.getSubmittedAt()).getSeconds();
        }
        return null;
    }

    private static List<SnapshotOption> options(SnapshotQuestion q) {
        return q.getOptions() == null ? List.of() : q.getOptions();
    }

    private static Comparator<SnapshotOption> optionOrder() {
        return Comparator.comparingInt(o -> o.getOptionOrder() == null ? 0 : o.getOptionOrder());
    }
}
