package com.centerton.bodybuddy.domain.analysis.client;

import com.centerton.bodybuddy.domain.meal.entity.NutritionValues;

import java.math.BigDecimal;

public record FoodNutritionEstimationResponse(
        NutritionValues nutrition,
        BigDecimal confidence,
        String provider,
        String model,
        String promptVersion,
        String providerResponseId,
        Integer inputTokens,
        Integer outputTokens
) {
}
