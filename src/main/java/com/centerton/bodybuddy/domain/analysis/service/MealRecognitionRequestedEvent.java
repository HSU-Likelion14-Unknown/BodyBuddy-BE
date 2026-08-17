package com.centerton.bodybuddy.domain.analysis.service;

public record MealRecognitionRequestedEvent(
        String mealId,
        String analysisRunId
) {
}
