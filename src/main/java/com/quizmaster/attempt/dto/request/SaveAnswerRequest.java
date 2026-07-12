package com.quizmaster.attempt.dto.request;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.Map;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SaveAnswerRequest {

    @NotNull(message = "Quiz question UUID is required")
    private UUID quizQuestionUuid;

    // For SINGLE_CHOICE, MULTIPLE_CHOICE — UUIDs of selected options
    private List<UUID> selectedOptionUuids;

    // For SHORT_ANSWER, LONG_ANSWER, FILL_IN_THE_BLANK, CODE, NUMERICAL
    @Size(max = 50000, message = "Text answer must not exceed 50000 characters")
    private String textAnswer;

    // For ORDERING — ordered list of option UUIDs
    private List<UUID> orderedOptionUuids;

    // For MATCHING — key=optionUuid, value=matchedOptionUuid
    private Map<UUID, UUID> matchPairs;

    // For TRUE_FALSE
    private Boolean booleanAnswer;

    // Whether student used a hint
    @Builder.Default
    private Boolean hintUsed = false;

    // Time spent on this question (seconds)
    @Min(value = 0, message = "Time spent cannot be negative")
    @Builder.Default
    private Integer timeSpentSeconds = 0;

    // Whether student flagged for review
    @Builder.Default
    private Boolean isFlagged = false;
}
