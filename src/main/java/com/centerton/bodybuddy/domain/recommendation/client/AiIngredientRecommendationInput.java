package com.centerton.bodybuddy.domain.recommendation.client;

import com.centerton.bodybuddy.domain.recommendation.model.KdrReferenceValues;
import com.centerton.bodybuddy.domain.recommendation.model.NutritionGapResult;
import com.centerton.bodybuddy.domain.recommendation.model.TargetNutrient;

import java.math.BigDecimal;
import java.util.List;

public record AiIngredientRecommendationInput(
        TargetNutrient targetNutrient,
        KdrReferenceValues reference,
        NutritionGapResult nutritionGap,
        int requestedCount,
        BigDecimal minimumTargetCoveragePercent,
        List<String> allergyCodes,
        List<String> dislikedFoods,
        List<String> excludedIngredientNames
) {
    public AiIngredientRecommendationInput {
        allergyCodes = allergyCodes == null ? List.of() : List.copyOf(allergyCodes);
        dislikedFoods = dislikedFoods == null ? List.of() : List.copyOf(dislikedFoods);
        excludedIngredientNames = excludedIngredientNames == null
                ? List.of()
                : List.copyOf(excludedIngredientNames);
    }
}
