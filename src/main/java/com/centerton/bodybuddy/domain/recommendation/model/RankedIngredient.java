package com.centerton.bodybuddy.domain.recommendation.model;

import com.centerton.bodybuddy.domain.meal.entity.NutritionValues;

import java.math.BigDecimal;

public record RankedIngredient(
        String foodId,
        String ingredientName,
        int rank,
        TargetNutrient targetNutrient,
        BigDecimal targetAmountPerServing,
        BigDecimal dailyTargetCoverageRatio,
        BigDecimal overallDeficiencyCoverageScore,
        NutritionValues nutritionPerServing
) {
}
