package com.centerton.bodybuddy.domain.recommendation.model;

import com.centerton.bodybuddy.domain.meal.entity.NutritionValues;

import java.math.BigDecimal;

public record RankedIngredient(
        String foodId,
        String ingredientName,
        int rank,
        TargetNutrient targetNutrient,
        BigDecimal targetAmountPer100g,
        BigDecimal dailyTargetCoverageRatio,
        BigDecimal overallDeficiencyCoverageScore,
        NutritionValues nutritionPer100g
) {
}
