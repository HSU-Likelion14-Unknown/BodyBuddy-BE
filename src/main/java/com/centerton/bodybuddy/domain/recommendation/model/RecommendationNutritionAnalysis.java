package com.centerton.bodybuddy.domain.recommendation.model;

import java.util.List;

public record RecommendationNutritionAnalysis(
        NutritionGapResult nutritionGap,
        List<RankedIngredient> ingredients
) {

    public RecommendationNutritionAnalysis {
        ingredients = List.copyOf(ingredients);
    }
}
