package com.centerton.bodybuddy.domain.recommendation.model;

import java.util.List;

public record RecommendationPlan(
        NutritionGapResult nutritionGap,
        List<IngredientDishRecommendation> ingredients
) {

    public RecommendationPlan {
        ingredients = List.copyOf(ingredients);
    }
}
